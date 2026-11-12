package com.training.fraudcase.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.PolicyCitationRef;
import com.training.fraudcase.agent.common.RuleApplication;
import com.training.fraudcase.api.dto.CaseSummaryView;
import com.training.fraudcase.api.dto.CreateCaseRequest;
import com.training.fraudcase.api.dto.ReviewRequest;
import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.CaseStateException;
import com.training.fraudcase.domain.ChatTurn;
import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.domain.ToolTraceView;
import com.training.fraudcase.domain.enums.ActorType;
import com.training.fraudcase.domain.enums.CaseStatus;
import com.training.fraudcase.domain.enums.Disposition;
import com.training.fraudcase.knowledge.PolicyRetriever;
import com.training.fraudcase.persistence.AuditEventWriter;
import com.training.fraudcase.persistence.entity.CaseMessageEntity;
import com.training.fraudcase.persistence.entity.FraudCaseEntity;
import com.training.fraudcase.persistence.entity.PolicyCitationEntity;
import com.training.fraudcase.persistence.entity.RecommendationEntity;
import com.training.fraudcase.persistence.entity.ReviewDispositionEntity;
import com.training.fraudcase.persistence.entity.TransactionClaimEntity;
import com.training.fraudcase.persistence.repo.CaseMessageRepository;
import com.training.fraudcase.persistence.repo.EvidenceItemRepository;
import com.training.fraudcase.persistence.repo.FraudCaseRepository;
import com.training.fraudcase.persistence.repo.PolicyCitationRepository;
import com.training.fraudcase.persistence.repo.RecommendationRepository;
import com.training.fraudcase.persistence.repo.ReviewDispositionRepository;
import com.training.fraudcase.persistence.repo.ToolTraceRepository;
import com.training.fraudcase.persistence.repo.TransactionClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Application service: intake validation/masking, state transitions,
 * review disposition, follow-up questions and read views. State changes
 * and review always require a human actor — never model-driven.
 */
@Service
public class FraudCaseService {

    /** Reject anything resembling a raw PAN/account number in masked refs. */
    private static final Pattern PAN_LIKE = Pattern.compile("\\d{9,}");
    /** Reject markup/script content in free text. */
    private static final Pattern MARKUP = Pattern.compile(
            "(<[^>]+>|\\$\\{|#\\{|javascript:|\\bscript\\b)", Pattern.CASE_INSENSITIVE);

    private final FraudCaseRepository caseRepo;
    private final TransactionClaimRepository claimRepo;
    private final EvidenceItemRepository evidenceRepo;
    private final ToolTraceRepository traceRepo;
    private final RecommendationRepository recommendationRepo;
    private final PolicyCitationRepository citationRepo;
    private final ReviewDispositionRepository reviewRepo;
    private final CaseMessageRepository messageRepo;
    private final PolicyRetriever policyRetriever;
    private final ProcessorRegistry processorRegistry;
    private final AuditEventWriter audit;
    private final ObjectMapper objectMapper;

    public FraudCaseService(FraudCaseRepository caseRepo,
                            TransactionClaimRepository claimRepo,
                            EvidenceItemRepository evidenceRepo,
                            ToolTraceRepository traceRepo,
                            RecommendationRepository recommendationRepo,
                            PolicyCitationRepository citationRepo,
                            ReviewDispositionRepository reviewRepo,
                            CaseMessageRepository messageRepo,
                            PolicyRetriever policyRetriever,
                            ProcessorRegistry processorRegistry,
                            AuditEventWriter audit,
                            ObjectMapper objectMapper) {
        this.caseRepo = caseRepo;
        this.claimRepo = claimRepo;
        this.evidenceRepo = evidenceRepo;
        this.traceRepo = traceRepo;
        this.recommendationRepo = recommendationRepo;
        this.citationRepo = citationRepo;
        this.reviewRepo = reviewRepo;
        this.messageRepo = messageRepo;
        this.policyRetriever = policyRetriever;
        this.processorRegistry = processorRegistry;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    // ---- Intake -------------------------------------------------------

    @Transactional
    public CaseSummaryView createCase(CreateCaseRequest req, String analystId) {
        validateIntake(req);
        CaseSummaryView open = findOpenDuplicate(req);
        if (open != null) {
            audit.append(open.caseId(), "DUPLICATE_INTAKE_RETURNED", ActorType.AGENT,
                    "identical intake suppressed for " + req.transactionRef() + " by=" + analystId);
            return open;
        }
        String caseId = nextCaseId();
        Instant now = Instant.now();

        FraudCaseEntity kase = new FraudCaseEntity();
        kase.setCaseId(caseId);
        kase.setMaskedCustomerRef(req.maskedCustomerRef());
        kase.setHomeRegion(req.homeRegion());
        kase.setStatus(CaseStatus.NEW);
        kase.setCaseChannel(req.channel());
        kase.setReportedAt(req.reportedAt());
        kase.setAnalystId(analystId);
        kase.setCreatedBy(analystId);
        kase.setCreatedAt(now);
        kase.setUpdatedAt(now);
        caseRepo.save(kase);

        TransactionClaimEntity claim = new TransactionClaimEntity();
        claim.setClaimId("CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        claim.setCaseId(caseId);
        claim.setTransactionRef(req.transactionRef());
        claim.setAmount(req.amount());
        claim.setCurrency(req.currency());
        claim.setOccurredAt(req.occurredAt());
        claim.setTransactionStatus(req.transactionStatus());
        claim.setTransactionChannel(req.transactionChannel());
        claim.setMaskedMerchant(req.maskedMerchant());
        claim.setIssueType(req.issueType());
        claim.setStatement(req.customerStatement());
        claim.setCardPossession(req.cardPossession());
        claim.setMerchantRecognised(req.merchantRecognised());
        claim.setSyntheticDataConfirmed(req.syntheticDataConfirmed());
        claim.setReviewRequired(req.reviewRequired());
        claimRepo.save(claim);

        audit.append(caseId, "CASE_CREATED", ActorType.AGENT,
                "channel=" + req.channel() + " issueType=" + req.issueType() + " by=" + analystId);
        return summary(kase, claim);
    }

    private void validateIntake(CreateCaseRequest req) {
        List<String> problems = new ArrayList<>();
        for (var entry : List.of(
                new String[]{"maskedCustomerRef", req.maskedCustomerRef()},
                new String[]{"transactionRef", req.transactionRef()},
                new String[]{"maskedMerchant", req.maskedMerchant()})) {
            if (entry[1] != null && PAN_LIKE.matcher(entry[1]).find()) {
                problems.add(entry[0] + " appears to contain an unmasked account/card number");
            }
        }
        if (req.customerStatement() != null && MARKUP.matcher(req.customerStatement()).find()) {
            problems.add("customerStatement contains markup or script content");
        }
        if (!req.syntheticDataConfirmed() || !req.reviewRequired()) {
            problems.add("syntheticDataConfirmed and reviewRequired must both be true");
        }
        if (!problems.isEmpty()) {
            throw new IntakeValidationException(problems);
        }
    }

    /** Duplicate-intake protection: an identical open case (same transaction
     *  reference, masked customer and issue type) still in NEW/ENRICHING is
     *  returned rather than creating a second investigation of the same claim. */
    private CaseSummaryView findOpenDuplicate(CreateCaseRequest req) {
        for (TransactionClaimEntity claim : claimRepo.findByTransactionRef(req.transactionRef())) {
            FraudCaseEntity kase = caseRepo.findById(claim.getCaseId()).orElse(null);
            if (kase == null) {
                continue;
            }
            boolean open = kase.getStatus() == CaseStatus.NEW || kase.getStatus() == CaseStatus.ENRICHING;
            if (open && req.maskedCustomerRef().equals(kase.getMaskedCustomerRef())
                    && req.issueType() == claim.getIssueType()) {
                return summary(kase, claim);
            }
        }
        return null;
    }

    /** Next ID derives from the highest existing case ID — safe across
     *  restarts and gaps. The caseId primary key remains the hard guard;
     *  concurrent creators may get a constraint violation rather than an
     *  overwrite. */
    private String nextCaseId() {
        int next = caseRepo.findTopByOrderByCaseIdDesc()
                .map(FraudCaseEntity::getCaseId)
                .map(id -> {
                    int dash = id.lastIndexOf('-');
                    try {
                        return dash >= 0 ? Integer.parseInt(id.substring(dash + 1)) : 0;
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0) + 1;
        return "CASE-2026-" + String.format("%04d", next);
    }

    // ---- Reads --------------------------------------------------------

    public FraudCaseEntity loadCase(String caseId) {
        return caseRepo.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    public TransactionClaimEntity loadClaim(String caseId) {
        return claimRepo.findFirstByCaseIdOrderByClaimIdAsc(caseId)
                .orElseThrow(() -> new CaseNotFoundException("claim for " + caseId));
    }

    public List<CaseSummaryView> listCases() {
        return caseRepo.findAll().stream()
                .map(k -> summary(k, claimRepo.findFirstByCaseIdOrderByClaimIdAsc(k.getCaseId()).orElse(null)))
                .toList();
    }

    public CaseSummaryView getSummary(String caseId) {
        return summary(loadCase(caseId), loadClaim(caseId));
    }

    private CaseSummaryView summary(FraudCaseEntity k, TransactionClaimEntity c) {
        return new CaseSummaryView(k.getCaseId(), k.getStatus(), k.getMaskedCustomerRef(),
                c == null ? null : c.getMaskedMerchant(),
                c == null ? null : c.getIssueType(),
                c == null ? null : c.getAmount(),
                c == null ? null : c.getCurrency(),
                k.getReportedAt(), k.getCreatedBy());
    }

    public CaseFacts factsFrom(FraudCaseEntity k, TransactionClaimEntity c) {
        return new CaseFacts(k.getCaseId(), k.getReportedAt(), k.getCaseChannel(), k.getAnalystId(),
                k.getMaskedCustomerRef(), k.getHomeRegion(),
                c.getTransactionRef(), c.getAmount(), c.getCurrency(), c.getOccurredAt(),
                c.getTransactionStatus(), c.getTransactionChannel(), c.getMaskedMerchant(),
                c.getIssueType(), c.getStatement(), c.getCardPossession(), c.getMerchantRecognised());
    }

    public List<EvidenceItemView> evidenceViews(String caseId) {
        loadCase(caseId);
        return evidenceRepo.findByCaseIdOrderByObservedAtAsc(caseId).stream()
                .map(e -> new EvidenceItemView(e.getEvidenceId(), e.getType(), e.getSeverity(),
                        e.getValueSummary(), e.getProvenance(), e.getObservedAt(), e.getSourceTool()))
                .toList();
    }

    public List<ToolTraceView> traceViews(String caseId) {
        loadCase(caseId);
        return traceRepo.findByCaseIdOrderByInvokedAtAsc(caseId).stream()
                .map(t -> new ToolTraceView(t.getTraceId(), t.getToolName(), t.getRequestHash(),
                        t.getStatus(), t.getDurationMs(), t.getResultSummary(), t.getInvokedAt()))
                .toList();
    }

    public FraudCaseRecommendation latestRecommendation(String caseId) {
        loadCase(caseId);
        RecommendationEntity e = recommendationRepo.findFirstByCaseIdOrderByCreatedAtDesc(caseId)
                .orElse(null);
        if (e == null) {
            return null;
        }
        List<PolicyCitationRef> citations = citationRepo.findByRecommendationId(e.getRecommendationId())
                .stream().map(c -> new PolicyCitationRef(c.getDocumentId(), c.getVersion(),
                        c.getSection(), c.getChunkId(), c.getScore()))
                .toList();
        return new FraudCaseRecommendation(caseId, e.getClassification(), e.getRiskLevel(),
                e.getRoute(), e.getConfidence(), e.isHumanReviewRequired(),
                readList(e.getEvidenceIdsJson(), String.class), citations,
                readList(e.getMissingInformationJson(), String.class),
                readList(e.getRuleIdsJson(), String.class),
                readList(e.getToolTraceIdsJson(), String.class), e.getRationale(),
                readRules(e.getAppliedRulesJson()));
    }

    public List<PolicyCitationRef> citations(String caseId) {
        loadCase(caseId);
        return citationRepo.findByCaseIdOrderByScoreDesc(caseId).stream()
                .map(c -> new PolicyCitationRef(c.getDocumentId(), c.getVersion(),
                        c.getSection(), c.getChunkId(), c.getScore()))
                .toList();
    }

    public List<ChatTurn> messages(String caseId) {
        loadCase(caseId);
        return messageRepo.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    public List<ReviewDispositionEntity> reviews(String caseId) {
        loadCase(caseId);
        return reviewRepo.findByCaseIdOrderByRecordedAtDesc(caseId);
    }

    // ---- State transitions -------------------------------------------

    public void transition(FraudCaseEntity kase, CaseStatus target, String auditType, String actorId) {
        CaseStatus from = kase.getStatus();
        if (from != target && !from.canTransitionTo(target)) {
            throw new CaseStateException(
                    "Illegal transition " + from + " -> " + target + " for case " + kase.getCaseId());
        }
        kase.setStatus(target);
        kase.setUpdatedAt(Instant.now());
        caseRepo.save(kase);
        if (from != target) {
            audit.append(kase.getCaseId(), auditType, ActorType.SYSTEM,
                    from + " -> " + target + (actorId != null ? " by=" + actorId : ""));
        }
    }

    // ---- Review --------------------------------------------------------

    @Transactional
    public ReviewDispositionEntity recordReview(String caseId, ReviewRequest req, String reviewerId) {
        FraudCaseEntity kase = loadCase(caseId);
        CaseStatus status = kase.getStatus();
        if (status != CaseStatus.READY_FOR_REVIEW && status != CaseStatus.UNDER_REVIEW) {
            throw new CaseStateException("Case " + caseId + " is " + status
                    + "; reviews require READY_FOR_REVIEW or UNDER_REVIEW");
        }
        if ((req.disposition() == Disposition.REROUTED || req.disposition() == Disposition.REVISED)
                && req.selectedRoute() == null) {
            throw new IntakeValidationException(List.of(
                    "selectedRoute is required for " + req.disposition()));
        }
        if (status == CaseStatus.READY_FOR_REVIEW) {
            transition(kase, CaseStatus.UNDER_REVIEW, "REVIEW_STARTED", reviewerId);
        }

        ReviewDispositionEntity review = new ReviewDispositionEntity();
        review.setReviewId("REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        review.setCaseId(caseId);
        review.setReviewerId(reviewerId);
        review.setDisposition(req.disposition());
        review.setReason(req.reason());
        review.setSelectedRoute(req.selectedRoute());
        review.setRecordedAt(Instant.now());
        reviewRepo.save(review);

        switch (req.disposition()) {
            case ACCEPTED, REVISED, REROUTED -> transition(kase, CaseStatus.ROUTED, "REVIEW_RECORDED", reviewerId);
            case REJECTED -> transition(kase, CaseStatus.CLOSED, "REVIEW_RECORDED", reviewerId);
            case MORE_INFO_REQUESTED -> {
                /* stays UNDER_REVIEW; follow-up enrichment may reopen via new evidence */
            }
        }
        audit.append(caseId, "REVIEW_RECORDED", ActorType.REVIEWER,
                "disposition=" + req.disposition() + " reviewer=" + reviewerId
                        + " route=" + req.selectedRoute());
        return review;
    }

    // ---- Follow-up questions ------------------------------------------

    @Transactional
    public ChatTurn postMessage(String caseId, String question, String authorId) {
        FraudCaseEntity kase = loadCase(caseId);
        saveMessage(caseId, "user", question);
        audit.append(caseId, "FOLLOW_UP_ASKED", ActorType.INVESTIGATOR, "by=" + authorId);

        TransactionClaimEntity claim = loadClaim(caseId);
        CaseFacts facts = factsFrom(kase, claim);
        List<EvidenceItemView> evidence = evidenceViews(caseId);
        List<ToolTraceView> traces = traceViews(caseId);
        List<ChatTurn> conversation = messages(caseId);
        // Retrieval scoped to the question — citations stay exact.
        List<PolicyPassage> passages = policyRetriever.retrieve(question + " " + facts.issueType());

        var ctx = new com.training.fraudcase.agent.common.FraudCaseContext(
                caseId, "Answer the investigator's follow-up question using stored evidence and "
                + "retrieved policy only; never invent facts.", facts, evidence, passages,
                traces, conversation, List.of());
        String answer = processorRegistry.active().answer(ctx, question);
        return saveMessage(caseId, "assistant", answer);
    }

    private ChatTurn saveMessage(String caseId, String role, String content) {
        CaseMessageEntity m = new CaseMessageEntity();
        m.setMessageId("MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        m.setCaseId(caseId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(Instant.now());
        messageRepo.save(m);
        return new ChatTurn(role, content, m.getCreatedAt());
    }

    private <T> List<T> readList(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<RuleApplication> readRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
