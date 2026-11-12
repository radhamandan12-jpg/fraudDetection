package com.training.fraudcase.api;

import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.PolicyCitationRef;
import com.training.fraudcase.api.dto.CaseSummaryView;
import com.training.fraudcase.api.dto.CreateCaseRequest;
import com.training.fraudcase.api.dto.MessageRequest;
import com.training.fraudcase.api.dto.ReviewRequest;
import com.training.fraudcase.application.FraudCaseService;
import com.training.fraudcase.application.InvestigationOrchestrator;
import com.training.fraudcase.application.InvestigationResult;
import com.training.fraudcase.domain.ChatTurn;
import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.ToolTraceView;
import com.training.fraudcase.persistence.entity.ReviewDispositionEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Minimum API surface (§5.1). Analyst/reviewer identity comes from the
 * caller identity header in this training build — production deployments
 * must put real authentication in front.
 */
@RestController
@RequestMapping("/api/cases")
public class FraudCaseController {

    public static final String ANALYST_HEADER = "X-Analyst-Id";
    public static final String REVIEWER_HEADER = "X-Reviewer-Id";

    private final FraudCaseService caseService;
    private final InvestigationOrchestrator orchestrator;

    public FraudCaseController(FraudCaseService caseService, InvestigationOrchestrator orchestrator) {
        this.caseService = caseService;
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ResponseEntity<CaseSummaryView> create(@Valid @RequestBody CreateCaseRequest req,
                                                  @RequestHeader(value = ANALYST_HEADER, defaultValue = "agent-anon") String analystId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.createCase(req, analystId));
    }

    @GetMapping
    public List<CaseSummaryView> list() {
        return caseService.listCases();
    }

    @GetMapping("/{caseId}")
    public CaseSummaryView get(@PathVariable String caseId) {
        return caseService.getSummary(caseId);
    }

    @PostMapping("/{caseId}/investigations")
    public InvestigationResult investigate(@PathVariable String caseId,
                                           @RequestHeader(value = ANALYST_HEADER, defaultValue = "agent-anon") String analystId) {
        return orchestrator.investigate(caseId, analystId);
    }

    @GetMapping("/{caseId}/evidence")
    public List<EvidenceItemView> evidence(@PathVariable String caseId) {
        return caseService.evidenceViews(caseId);
    }

    @GetMapping("/{caseId}/tool-traces")
    public List<ToolTraceView> toolTraces(@PathVariable String caseId) {
        return caseService.traceViews(caseId);
    }

    @GetMapping("/{caseId}/recommendation")
    public ResponseEntity<FraudCaseRecommendation> recommendation(@PathVariable String caseId) {
        FraudCaseRecommendation rec = caseService.latestRecommendation(caseId);
        return rec == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(rec);
    }

    @GetMapping("/{caseId}/citations")
    public List<PolicyCitationRef> citations(@PathVariable String caseId) {
        return caseService.citations(caseId);
    }

    @PostMapping("/{caseId}/messages")
    public ChatTurn message(@PathVariable String caseId,
                            @Valid @RequestBody MessageRequest req,
                            @RequestHeader(value = ANALYST_HEADER, defaultValue = "agent-anon") String analystId) {
        return caseService.postMessage(caseId, req.question(), analystId);
    }

    @GetMapping("/{caseId}/messages")
    public List<ChatTurn> messages(@PathVariable String caseId) {
        return caseService.messages(caseId);
    }

    @PostMapping("/{caseId}/reviews")
    public ReviewDispositionEntity review(@PathVariable String caseId,
                                          @Valid @RequestBody ReviewRequest req,
                                          @RequestHeader(value = REVIEWER_HEADER, defaultValue = "reviewer-anon") String reviewerId) {
        return caseService.recordReview(caseId, req, reviewerId);
    }

    @GetMapping("/{caseId}/audit-events")
    public Object auditEvents(@PathVariable String caseId) {
        caseService.loadCase(caseId);
        return Map.of("caseId", caseId, "note",
                "Audit events are persisted append-only in the audit_events table (US-07).");
    }
}
