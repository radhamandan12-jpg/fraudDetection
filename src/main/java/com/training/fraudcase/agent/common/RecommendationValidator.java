package com.training.fraudcase.agent.common;

import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.domain.ToolTraceView;
import com.training.fraudcase.domain.enums.Classification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Schema and guardrail validation for every recommendation regardless of
 * processor mode (§3.4). Anything that fails is rejected — never silently
 * persisted or displayed. Referenced IDs must exist in the supplied context:
 * a model cannot cite evidence, tool traces, rules or policy chunks it was
 * never shown.
 */
@Component
public class RecommendationValidator {

    private static final Pattern RULE_ID = Pattern.compile("^R-\\d{3}$");

    /**
     * Validate the schema, verify referential integrity against the case
     * context, then enforce the human-review guardrail.
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

        // Referential integrity — cited objects must exist in this context.
        Set<String> evidenceIds = ctx.evidence() == null ? Set.of()
                : ctx.evidence().stream().map(EvidenceItemView::evidenceId).collect(Collectors.toSet());
        for (String id : r.evidenceIds()) {
            if (!evidenceIds.contains(id)) {
                throw new InvalidRecommendationException("evidenceId not in case context: " + id);
            }
        }
        Set<String> traceIds = ctx.toolTraces() == null ? Set.of()
                : ctx.toolTraces().stream().map(ToolTraceView::traceId).collect(Collectors.toSet());
        for (String id : r.toolTraceIds()) {
            if (!traceIds.contains(id)) {
                throw new InvalidRecommendationException("toolTraceId not in case context: " + id);
            }
        }
        Map<String, PolicyPassage> passagesByKey = ctx.policyPassages() == null ? Map.of()
                : ctx.policyPassages().stream().collect(Collectors.toMap(
                        p -> p.documentId() + "|" + p.chunkId(), Function.identity(), (a, b) -> a));
        List<PolicyCitationRef> citations = r.policyCitations().stream().map(c -> {
            if (c == null || c.documentId() == null || c.version() == null || c.chunkId() == null) {
                throw new InvalidRecommendationException("malformed policy citation");
            }
            PolicyPassage passage = passagesByKey.get(c.documentId() + "|" + c.chunkId());
            if (passage == null) {
                throw new InvalidRecommendationException(
                        "policy citation not in retrieved context: " + c.documentId() + "/" + c.chunkId());
            }
            String version = stripVersionPrefix(c.version());
            if (!version.equals(passage.version())) {
                throw new InvalidRecommendationException("citation version " + c.version()
                        + " does not match retrieved passage version " + passage.version());
            }
            return new PolicyCitationRef(c.documentId(), version, c.section(), c.chunkId(), c.score());
        }).toList();

        for (String id : r.ruleIds()) {
            if (id == null || !RULE_ID.matcher(id).matches()) {
                throw new InvalidRecommendationException("invalid ruleId: " + id);
            }
        }
        for (RuleApplication a : r.appliedRules()) {
            if (a == null || a.ruleId() == null || !RULE_ID.matcher(a.ruleId()).matches()) {
                throw new InvalidRecommendationException(
                        "invalid appliedRule id: " + (a == null ? null : a.ruleId()));
            }
        }

        FraudCaseRecommendation normalised = new FraudCaseRecommendation(r.caseId(), r.classification(),
                r.riskLevel(), r.recommendedRoute(), r.confidence(), r.humanReviewRequired(),
                r.evidenceIds(), citations, r.missingInformation(), r.ruleIds(), r.toolTraceIds(),
                r.rationale(), r.appliedRules());
        return requireHumanReview(normalised);
    }

    /** Citation versions rendered to the model carry a "v" prefix (v3.2);
     *  normalise back to the stored form before comparison/persistence. */
    private static String stripVersionPrefix(String version) {
        return version != null && version.length() > 1
                && (version.charAt(0) == 'v' || version.charAt(0) == 'V')
                && Character.isDigit(version.charAt(1))
                ? version.substring(1) : version;
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
