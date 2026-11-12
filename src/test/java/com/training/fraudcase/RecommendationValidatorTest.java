package com.training.fraudcase;

import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.agent.common.PolicyCitationRef;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.RiskLevel;
import com.training.fraudcase.domain.enums.Route;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.training.fraudcase.TestFixtures.ctx;
import static com.training.fraudcase.TestFixtures.facts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationValidatorTest {

    private final RecommendationValidator validator = new RecommendationValidator();
    private final FraudCaseContext context = ctx(
            facts("CASE-2026-0001", "TXN-FRAUD-1", IssueType.UNRECOGNISED_TRANSACTION, false),
            List.of(), List.of(), List.of());

    private FraudCaseRecommendation rec(Classification c, Route r, Double conf,
                                        List<String> evidence, Boolean human) {
        return new FraudCaseRecommendation("CASE-2026-0001", c, RiskLevel.HIGH, r, conf,
                human, evidence, List.of(), List.of(), List.of(), List.of(), "rationale", List.of());
    }

    @Test
    void acceptsValidRecommendation() {
        var r = rec(Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                0.86, List.of("EV-1"), true);
        assertEquals(r, validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsConfidenceOutOfRange() {
        var r = rec(Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                1.5, List.of("EV-1"), true);
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsMismatchedCaseId() {
        var r = new FraudCaseRecommendation("OTHER", Classification.GENUINE_TRANSACTION,
                RiskLevel.LOW, Route.MANUAL_REVIEW, 0.5, true, List.of("EV-1"),
                List.of(), List.of(), List.of(), List.of(), "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }

    @Test
    void requiresEvidenceOrExplicitInsufficient() {
        var noEvidence = rec(Classification.SUSPICIOUS_TRANSACTION, Route.FRAUD_INVESTIGATION,
                0.5, List.of(), true);
        assertThrows(InvalidRecommendationException.class,
                () -> validator.validateAndEnforce(noEvidence, context));
        var insufficient = rec(Classification.INSUFFICIENT_EVIDENCE, Route.MANUAL_REVIEW,
                0.5, List.of(), true);
        validator.validateAndEnforce(insufficient, context); // passes
    }

    @Test
    void enforcesHumanReviewRequired() {
        var r = rec(Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                0.86, List.of("EV-1"), false);
        var out = validator.validateAndEnforce(r, context);
        assertTrue(out.humanReviewRequired());
    }

    @Test
    void rejectsMalformedCitation() {
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-1"),
                List.of(new PolicyCitationRef("POL-1", null, "s", "c", 0.5)),
                List.of(), List.of(), List.of(), "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }
}
