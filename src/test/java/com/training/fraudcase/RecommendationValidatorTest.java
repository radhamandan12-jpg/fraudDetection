package com.training.fraudcase;

import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.agent.common.PolicyCitationRef;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.agent.common.RuleApplication;
import com.training.fraudcase.domain.ToolTraceView;
import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.RiskLevel;
import com.training.fraudcase.domain.enums.Route;
import com.training.fraudcase.domain.enums.ToolOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.training.fraudcase.TestFixtures.ctx;
import static com.training.fraudcase.TestFixtures.facts;
import static com.training.fraudcase.TestFixtures.passage;
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

    private FraudCaseContext ctxWithTracesAndPassages() {
        var base = context;
        var traces = List.of(new ToolTraceView("TR-ABC12345", "getTransactionDetails",
                "hash", ToolOutcome.OK, 5L, "ok", Instant.now()));
        return new FraudCaseContext(base.caseId(), base.investigationGoal(), base.facts(),
                base.evidence(), List.of(passage()), traces, base.conversation(), List.of());
    }

    @Test
    void acceptsValidRecommendation() {
        var r = rec(Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                0.86, List.of("EV-TEST001"), true);
        assertEquals(r, validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsConfidenceOutOfRange() {
        var r = rec(Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                1.5, List.of("EV-TEST001"), true);
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsMismatchedCaseId() {
        var r = new FraudCaseRecommendation("OTHER", Classification.GENUINE_TRANSACTION,
                RiskLevel.LOW, Route.MANUAL_REVIEW, 0.5, true, List.of("EV-TEST001"),
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
                0.86, List.of("EV-TEST001"), false);
        var out = validator.validateAndEnforce(r, context);
        assertTrue(out.humanReviewRequired());
    }

    @Test
    void rejectsMalformedCitation() {
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(new PolicyCitationRef("POL-1", null, "s", "c", 0.5)),
                List.of(), List.of(), List.of(), "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsEvidenceIdNotInContext() {
        var r = rec(Classification.UNAUTHORISED_TRANSACTION, Route.FRAUD_INVESTIGATION,
                0.86, List.of("EV-FABRICATED"), true);
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsFabricatedToolTraceId() {
        var ctx = ctxWithTracesAndPassages();
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(), List.of(), List.of(), List.of("TR-getTransactionDetails"),
                "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, ctx));
    }

    @Test
    void acceptsToolTraceIdInContext() {
        var ctx = ctxWithTracesAndPassages();
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(), List.of(), List.of(), List.of("TR-ABC12345"), "r", List.of());
        validator.validateAndEnforce(r, ctx); // passes
    }

    @Test
    void rejectsCitationNotInRetrievedContext() {
        var ctx = ctxWithTracesAndPassages();
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(new PolicyCitationRef("POL-OTHER-9", "1.0", "s", "other-c001", 0.5)),
                List.of(), List.of(), List.of(), "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, ctx));
    }

    @Test
    void normalisesCitationVersionPrefix() {
        var ctx = ctxWithTracesAndPassages();
        var p = passage();
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(new PolicyCitationRef(p.documentId(), "v" + p.version(), p.section(), p.chunkId(), p.score())),
                List.of(), List.of(), List.of(), "r", List.of());
        var out = validator.validateAndEnforce(r, ctx);
        assertEquals(p.version(), out.policyCitations().get(0).version());
    }

    @Test
    void rejectsCitationWithWrongVersion() {
        var ctx = ctxWithTracesAndPassages();
        var p = passage();
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(new PolicyCitationRef(p.documentId(), "9.9", p.section(), p.chunkId(), p.score())),
                List.of(), List.of(), List.of(), "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, ctx));
    }

    @Test
    void rejectsInvalidRuleIdFormat() {
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(), List.of(), List.of("MODEL-MADE-THIS-UP"), List.of(), "r", List.of());
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }

    @Test
    void rejectsMalformedAppliedRule() {
        var r = new FraudCaseRecommendation("CASE-2026-0001", Classification.UNAUTHORISED_TRANSACTION,
                RiskLevel.HIGH, Route.FRAUD_INVESTIGATION, 0.8, true, List.of("EV-TEST001"),
                List.of(), List.of(), List.of(), List.of(), "r",
                List.of(new RuleApplication("bad", "desc", List.of())));
        assertThrows(InvalidRecommendationException.class, () -> validator.validateAndEnforce(r, context));
    }
}
