package com.training.fraudcase.agent.common;

import com.training.fraudcase.domain.enums.Classification;
import org.springframework.stereotype.Component;

/**
 * Schema and guardrail validation for every recommendation regardless of
 * processor mode (§3.4). Anything that fails is rejected — never silently
 * persisted or displayed.
 */
@Component
public class RecommendationValidator {

    /**
     * Validate the schema, then enforce the human-review guardrail.
     *
     * @throws InvalidRecommendationException on any schema violation
     */
    public FraudCaseRecommendation validateAndEnforce(FraudCaseRecommendation r, FraudCaseContext ctx) {
        if (r == null) {
            throw new InvalidRecommendationException("empty recommendation");
        }
        if (r.caseId() == null || !r.caseId().equals(ctx.caseId())) {
            throw new InvalidRecommendationException("missing or mismatched caseId");
        }
        if (r.classification() == null) {
            throw new InvalidRecommendationException("missing classification");
        }
        if (r.riskLevel() == null) {
            throw new InvalidRecommendationException("missing riskLevel");
        }
        if (r.recommendedRoute() == null) {
            throw new InvalidRecommendationException("missing recommendedRoute");
        }
        if (r.confidence() == null || r.confidence() < 0.0 || r.confidence() > 1.0) {
            throw new InvalidRecommendationException("confidence outside 0-1: " + r.confidence());
        }
        boolean hasEvidence = !r.evidenceIds().isEmpty();
        boolean explicitInsufficient = r.classification() == Classification.INSUFFICIENT_EVIDENCE;
        if (!hasEvidence && !explicitInsufficient) {
            throw new InvalidRecommendationException(
                    "requires at least one evidence item or an explicit INSUFFICIENT_EVIDENCE outcome");
        }
        if (r.rationale() == null || r.rationale().isBlank()) {
            throw new InvalidRecommendationException("missing rationale");
        }
        for (PolicyCitationRef c : r.policyCitations()) {
            if (c == null || c.documentId() == null || c.version() == null || c.chunkId() == null) {
                throw new InvalidRecommendationException("malformed policy citation");
            }
        }
        return requireHumanReview(r);
    }

    /** Enforce humanReviewRequired=true on every recommendation. */
    public FraudCaseRecommendation requireHumanReview(FraudCaseRecommendation r) {
        if (Boolean.TRUE.equals(r.humanReviewRequired())) {
            return r;
        }
        return new FraudCaseRecommendation(r.caseId(), r.classification(), r.riskLevel(),
                r.recommendedRoute(), r.confidence(), Boolean.TRUE, r.evidenceIds(),
                r.policyCitations(), r.missingInformation(), r.ruleIds(), r.toolTraceIds(),
                r.rationale(), r.appliedRules());
    }
}
