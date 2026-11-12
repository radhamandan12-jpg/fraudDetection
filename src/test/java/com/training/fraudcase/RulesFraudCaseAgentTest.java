package com.training.fraudcase;

import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.rules.RulesFraudCaseAgent;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.Route;
import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.tools.SyntheticEvidenceAdapter;
import com.training.fraudcase.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.training.fraudcase.TestFixtures.adapter;
import static com.training.fraudcase.TestFixtures.ctx;
import static com.training.fraudcase.TestFixtures.facts;
import static com.training.fraudcase.TestFixtures.ok;
import static com.training.fraudcase.TestFixtures.passage;
import static com.training.fraudcase.TestFixtures.retrieverWith;
import static com.training.fraudcase.TestFixtures.unavailable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesFraudCaseAgentTest {

    private final SyntheticEvidenceAdapter adapter = adapter();

    private RulesFraudCaseAgent agent(List<PolicyPassage> passages) {
        return new RulesFraudCaseAgent(new RecommendationValidator(),
                retrieverWith(passages, 0.35));
    }

    private List<ToolResult<?>> toolResults(CaseFacts f) {
        return List.of(
                ok("getTransactionDetails", adapter.transactionDetails(f.transactionRef(), f.amount(), f.currency())),
                ok("getDeviceSignals", adapter.deviceSignals(f.maskedCustomerRef(), f.transactionRef())),
                ok("compareRecentLocations", adapter.recentLocations(f.maskedCustomerRef(), f.homeRegion(), f.transactionRef())),
                ok("getMerchantRiskProfile", adapter.merchantRisk(f.maskedMerchant())),
                ok("getPriorDisputes", adapter.priorDisputes(f.maskedCustomerRef())));
    }

    @Test
    void unauthorisedScenario() {
        CaseFacts f = facts("C1", "TXN-FRAUD-0042", IssueType.UNRECOGNISED_TRANSACTION, false);
        FraudCaseRecommendation r = agent(List.of(passage()))
                .analyze(ctx(f, List.of(), List.of(passage()), toolResults(f)));

        assertEquals(Classification.UNAUTHORISED_TRANSACTION, r.classification());
        assertEquals(Route.FRAUD_INVESTIGATION, r.recommendedRoute());
        assertTrue(r.ruleIds().contains("R-004"));
        assertTrue(r.humanReviewRequired());
        assertFalse(r.appliedRules().isEmpty());
        assertFalse(r.policyCitations().isEmpty());
    }

    @Test
    void duplicateFingerprintRoutesToPaymentOps() {
        CaseFacts f = facts("C2", "TXN-DUP-0007", IssueType.DUPLICATE_CHARGE, true);
        FraudCaseRecommendation r = agent(List.of(passage()))
                .analyze(ctx(f, List.of(), List.of(passage()), toolResults(f)));

        assertEquals(Classification.DUPLICATE_PROCESSING, r.classification());
        assertEquals(Route.PAYMENT_OPERATIONS, r.recommendedRoute());
        assertTrue(r.ruleIds().contains("R-001"));
    }

    @Test
    void missingTransactionIsInsufficientEvidence() {
        CaseFacts f = facts("C3", "TXN-MISSING-9", IssueType.UNRECOGNISED_TRANSACTION, false);
        var results = List.<ToolResult<?>>of(
                unavailable("getTransactionDetails"),
                ok("getDeviceSignals", adapter.deviceSignals(f.maskedCustomerRef(), f.transactionRef())),
                ok("compareRecentLocations", adapter.recentLocations(f.maskedCustomerRef(), f.homeRegion(), f.transactionRef())),
                ok("getMerchantRiskProfile", adapter.merchantRisk(f.maskedMerchant())),
                ok("getPriorDisputes", adapter.priorDisputes(f.maskedCustomerRef())));
        FraudCaseRecommendation r = agent(List.of(passage()))
                .analyze(ctx(f, List.of(), List.of(passage()), results));

        assertEquals(Classification.INSUFFICIENT_EVIDENCE, r.classification());
        assertEquals(Route.MANUAL_REVIEW, r.recommendedRoute());
        assertTrue(r.ruleIds().contains("R-002"));
        assertTrue(r.missingInformation().stream().anyMatch(m -> m.contains("transaction")));
    }

    @Test
    void recognisedMerchantFulfilmentIsDispute() {
        // Clean signals: trusted device, local activity, normal merchant —
        // the only story is the fulfilment dispute.
        CaseFacts f = facts("C4", "TXN-MERCH-1", IssueType.FULFILMENT_PROBLEM, true,
                "CUST-TRUSTED", "MRC-SHOP-12");
        FraudCaseRecommendation r = agent(List.of(passage()))
                .analyze(ctx(f, List.of(), List.of(passage()), toolResults(f)));

        assertEquals(Classification.MERCHANT_DISPUTE, r.classification());
        assertEquals(Route.DISPUTE_OPERATIONS, r.recommendedRoute());
        assertTrue(r.ruleIds().contains("R-003"));
    }

    @Test
    void noPolicyPassageFlagsGapAndRoutesManual() {
        CaseFacts f = facts("C5", "TXN-FRAUD-0042", IssueType.UNRECOGNISED_TRANSACTION, false);
        FraudCaseRecommendation r = agent(List.of())
                .analyze(ctx(f, List.of(), List.of(), toolResults(f)));

        assertEquals(Route.MANUAL_REVIEW, r.recommendedRoute());
        assertTrue(r.ruleIds().contains("R-007"));
        assertTrue(r.missingInformation().stream().anyMatch(m -> m.contains("policy")));
        assertTrue(r.policyCitations().isEmpty());
    }

    @Test
    void suspiciousWhenAnomaliesWithoutDenial() {
        CaseFacts f = facts("C6", "TXN-FRAUD-0042", IssueType.OTHER, true);
        FraudCaseRecommendation r = agent(List.of(passage()))
                .analyze(ctx(f, List.of(), List.of(passage()), toolResults(f)));

        assertTrue(r.classification() == Classification.SUSPICIOUS_TRANSACTION
                || r.classification() == Classification.MERCHANT_DISPUTE
                || r.classification() == Classification.GENUINE_TRANSACTION);
        assertTrue(r.humanReviewRequired());
    }
}
