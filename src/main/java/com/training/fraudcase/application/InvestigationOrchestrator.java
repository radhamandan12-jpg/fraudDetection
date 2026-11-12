package com.training.fraudcase.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseProcessor;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.CaseStateException;
import com.training.fraudcase.domain.ChatTurn;
import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.InvestigationPlan;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.domain.ToolTraceView;
import com.training.fraudcase.domain.enums.ActorType;
import com.training.fraudcase.domain.enums.CaseStatus;
import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.knowledge.PolicyRetriever;
import com.training.fraudcase.persistence.AuditEventWriter;
import com.training.fraudcase.persistence.entity.CaseMessageEntity;
import com.training.fraudcase.persistence.entity.EvidenceItemEntity;
import com.training.fraudcase.persistence.entity.FraudCaseEntity;
import com.training.fraudcase.persistence.entity.PolicyCitationEntity;
import com.training.fraudcase.persistence.entity.RecommendationEntity;
import com.training.fraudcase.persistence.entity.ToolTraceEntity;
import com.training.fraudcase.persistence.entity.TransactionClaimEntity;
import com.training.fraudcase.persistence.repo.CaseMessageRepository;
import com.training.fraudcase.persistence.repo.EvidenceItemRepository;
import com.training.fraudcase.persistence.repo.PolicyCitationRepository;
import com.training.fraudcase.persistence.repo.RecommendationRepository;
import com.training.fraudcase.persistence.repo.ToolTraceRepository;
import com.training.fraudcase.tools.CaseToolContext;
import com.training.fraudcase.tools.EvidenceTools;
import com.training.fraudcase.tools.ToolResult;
import com.training.fraudcase.tools.dto.CaseSla;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the bounded pipeline: Plan -> Act (allow-listed tools) -> Retrieve
 * (policy) -> Assess/Recommend (active processor) -> persist. Model output
 * never transitions state — the service does. Any processor error fails
 * safely to INCOMPLETE/manual review.
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    private final FraudCaseService caseService;
    private final InvestigationPlanner planner;
    private final EvidenceTools tools;
    private final EvidenceFactory evidenceFactory;
    private final PolicyRetriever policyRetriever;
    private final ProcessorRegistry processorRegistry;
    private final AuditEventWriter audit;
    private final EvidenceItemRepository evidenceRepo;
    private final ToolTraceRepository traceRepo;
    private final RecommendationRepository recommendationRepo;
    private final PolicyCitationRepository citationRepo;
    private final CaseMessageRepository messageRepo;
    private final ObjectMapper objectMapper;

    public InvestigationOrchestrator(FraudCaseService caseService,
                                     InvestigationPlanner planner,
                                     EvidenceTools tools,
                                     EvidenceFactory evidenceFactory,
                                     PolicyRetriever policyRetriever,
                                     ProcessorRegistry processorRegistry,
                                     AuditEventWriter audit,
                                     EvidenceItemRepository evidenceRepo,
                                     ToolTraceRepository traceRepo,
                                     RecommendationRepository recommendationRepo,
                                     PolicyCitationRepository citationRepo,
                                     CaseMessageRepository messageRepo,
                                     ObjectMapper objectMapper) {
        this.caseService = caseService;
        this.planner = planner;
        this.tools = tools;
        this.evidenceFactory = evidenceFactory;
        this.policyRetriever = policyRetriever;
        this.processorRegistry = processorRegistry;
        this.audit = audit;
        this.evidenceRepo = evidenceRepo;
        this.traceRepo = traceRepo;
        this.recommendationRepo = recommendationRepo;
        this.citationRepo = citationRepo;
        this.messageRepo = messageRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvestigationResult investigate(String caseId, String actorId) {
        FraudCaseEntity kase = caseService.loadCase(caseId);
        TransactionClaimEntity claim = caseService.loadClaim(caseId);
        CaseStatus status = kase.getStatus();
        if (status != CaseStatus.NEW && status != CaseStatus.INCOMPLETE) {
            throw new CaseStateException("Case " + caseId + " is " + status
                    + "; investigation can only start from NEW or INCOMPLETE");
        }

        caseService.transition(kase, CaseStatus.ENRICHING, "INVESTIGATION_STARTED", actorId);
        CaseToolContext.set(caseId);
        try {
            CaseFacts facts = caseService.factsFrom(kase, claim);

            // Stage 2 — Plan
            InvestigationPlan plan = planner.plan(facts);
            audit.append(caseId, "PLAN_CREATED", ActorType.SYSTEM,
                    "goal=" + abbreviate(plan.goal(), 200) + " tools=" + plan.plannedTools());

            // Stage 3 — Act: invoke the bounded allow-listed plan
            List<ToolResult<?>> results = runPlan(facts);

            // Evidence persistence (replace prior run's evidence)
            evidenceRepo.deleteByCaseId(caseId);
            List<EvidenceItemView> evidence = evidenceFactory.fromResults(results, facts);
            evidence.forEach(e -> evidenceRepo.save(toEntity(caseId, e)));

            // Stage 4 — Retrieve
            List<PolicyPassage> passages = policyRetriever.retrieve(plan.retrievalQuery());
            audit.append(caseId, "POLICY_RETRIEVED", ActorType.SYSTEM,
                    "query=" + abbreviate(plan.retrievalQuery(), 120) + " matches=" + passages.size());

            FraudCaseContext ctx = new FraudCaseContext(caseId, plan.goal(), facts, evidence,
                    passages, traceViews(caseId), conversation(caseId), results);

            // Stages 5+6 — Assess & Recommend via the configured processor
            FraudCaseProcessor processor = processorRegistry.active();
            FraudCaseRecommendation rec;
            try {
                rec = processor.analyze(ctx);
            } catch (RuntimeException e) {
                log.warn("processor {} failed safely for {}: {}", processor.mode(), caseId, e.toString());
                audit.append(caseId, "RECOMMENDATION_FAILED", ActorType.SYSTEM,
                        processor.mode() + ": " + abbreviate(e.getMessage(), 300));
                caseService.transition(kase, CaseStatus.INCOMPLETE, "STATE_CHANGED", actorId);
                return new InvestigationResult(CaseStatus.INCOMPLETE, processor.mode(), plan, null,
                        "Analysis failed safely; case routed to incomplete/manual review.");
            }

            // SLA target for the proposed route (also traced); stored as SLA evidence
            ToolResult<CaseSla> slaResult =
                    tools.calculateCaseSla(rec.recommendedRoute().name(), rec.riskLevel().name());
            evidenceFactory.fromResults(List.of(slaResult), facts).stream()
                    .filter(e -> e.type() == com.training.fraudcase.domain.enums.EvidenceType.SLA)
                    .forEach(e -> evidenceRepo.save(toEntity(caseId, e)));

            persistRecommendation(caseId, processor.mode(), rec);
            audit.append(caseId, "RECOMMENDATION_GENERATED", ActorType.SYSTEM,
                    "mode=" + processor.mode() + " classification=" + rec.classification()
                            + " route=" + rec.recommendedRoute() + " confidence=" + rec.confidence());

            CaseStatus next = rec.classification() == Classification.INSUFFICIENT_EVIDENCE
                    ? CaseStatus.INCOMPLETE : CaseStatus.READY_FOR_REVIEW;
            caseService.transition(kase, next, "STATE_CHANGED", actorId);
            return new InvestigationResult(next, processor.mode(), plan, rec, null);
        } finally {
            CaseToolContext.clear();
        }
    }

    private List<ToolResult<?>> runPlan(CaseFacts facts) {
        List<ToolResult<?>> results = new ArrayList<>();
        results.add(tools.getTransactionDetails(facts.transactionRef(), facts.amount(), facts.currency()));
        results.add(tools.getDeviceSignals(facts.maskedCustomerRef(), facts.transactionRef()));
        results.add(tools.compareRecentLocations(facts.maskedCustomerRef(), facts.homeRegion(), facts.transactionRef()));
        results.add(tools.getMerchantRiskProfile(facts.maskedMerchant()));
        results.add(tools.getPriorDisputes(facts.maskedCustomerRef()));
        results.add(tools.getTransactionVelocity(facts.maskedCustomerRef(), facts.transactionRef()));
        results.add(tools.checkAmountDeviation(facts.maskedCustomerRef(), facts.amount(), facts.transactionRef()));
        results.add(tools.getSanctionsScreening(facts.maskedCustomerRef(), facts.maskedMerchant()));
        return results;
    }

    private List<ToolTraceView> traceViews(String caseId) {
        return traceRepo.findByCaseIdOrderByInvokedAtAsc(caseId).stream()
                .map(t -> new ToolTraceView(t.getTraceId(), t.getToolName(), t.getRequestHash(),
                        t.getStatus(), t.getDurationMs(), t.getResultSummary(), t.getInvokedAt()))
                .toList();
    }

    private List<ChatTurn> conversation(String caseId) {
        return messageRepo.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    private void persistRecommendation(String caseId, String mode, FraudCaseRecommendation rec) {
        RecommendationEntity entity = new RecommendationEntity();
        String recId = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        entity.setRecommendationId(recId);
        entity.setCaseId(caseId);
        entity.setMode(mode);
        entity.setClassification(rec.classification());
        entity.setRoute(rec.recommendedRoute());
        entity.setRiskLevel(rec.riskLevel());
        entity.setConfidence(rec.confidence());
        entity.setRationale(rec.rationale());
        entity.setHumanReviewRequired(Boolean.TRUE.equals(rec.humanReviewRequired()));
        entity.setEvidenceIdsJson(writeJson(rec.evidenceIds()));
        entity.setMissingInformationJson(writeJson(rec.missingInformation()));
        entity.setRuleIdsJson(writeJson(rec.ruleIds()));
        entity.setToolTraceIdsJson(writeJson(rec.toolTraceIds()));
        entity.setAppliedRulesJson(writeJson(rec.appliedRules()));
        entity.setCreatedAt(Instant.now());
        recommendationRepo.save(entity);

        rec.policyCitations().forEach(c -> {
            PolicyCitationEntity ce = new PolicyCitationEntity();
            ce.setCitationId("CIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ce.setRecommendationId(recId);
            ce.setCaseId(caseId);
            ce.setDocumentId(c.documentId());
            ce.setVersion(c.version());
            ce.setSection(c.section());
            ce.setChunkId(c.chunkId());
            ce.setScore(c.score() == null ? 0.0 : c.score());
            citationRepo.save(ce);
        });
    }

    private EvidenceItemEntity toEntity(String caseId, EvidenceItemView v) {
        EvidenceItemEntity e = new EvidenceItemEntity();
        e.setEvidenceId(v.evidenceId());
        e.setCaseId(caseId);
        e.setType(v.type());
        e.setSeverity(v.severity());
        e.setValueSummary(v.valueSummary());
        e.setProvenance(v.provenance());
        e.setObservedAt(v.observedAt());
        e.setSourceTool(v.sourceTool());
        return e;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String abbreviate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
