package com.training.fraudcase.agent.rules;

import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseProcessor;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.PolicyCitationRef;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.agent.common.RuleApplication;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.domain.enums.AnomalyLevel;
import com.training.fraudcase.domain.enums.AuthOutcome;
import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.RiskLevel;
import com.training.fraudcase.domain.enums.Route;
import com.training.fraudcase.domain.enums.ToolOutcome;
import com.training.fraudcase.domain.enums.TrustLevel;
import com.training.fraudcase.knowledge.PolicyRetriever;
import com.training.fraudcase.tools.ToolResult;
import com.training.fraudcase.tools.dto.AmountDeviation;
import com.training.fraudcase.tools.dto.DeviceSignals;
import com.training.fraudcase.tools.dto.LocationComparison;
import com.training.fraudcase.tools.dto.MerchantRiskProfile;
import com.training.fraudcase.tools.dto.PriorDisputes;
import com.training.fraudcase.tools.dto.ScreeningResult;
import com.training.fraudcase.tools.dto.TransactionDetails;
import com.training.fraudcase.tools.dto.TransactionVelocity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Deterministic fallback processor (§3.2). No model dependency; every
 * result carries rule IDs and the exact input facts used.
 */
@Component
public class RulesFraudCaseAgent implements FraudCaseProcessor {

    private final RecommendationValidator validator;
    private final PolicyRetriever policyRetriever;

    public RulesFraudCaseAgent(RecommendationValidator validator, PolicyRetriever policyRetriever) {
        this.validator = validator;
        this.policyRetriever = policyRetriever;
    }

    @Override
    public String mode() {
        return "rules";
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext ctx) {
        List<RuleApplication> applied = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        ToolResult<TransactionDetails> txnRes = ctx.toolResult("getTransactionDetails", TransactionDetails.class);
        ToolResult<DeviceSignals> devRes = ctx.toolResult("getDeviceSignals", DeviceSignals.class);
        ToolResult<LocationComparison> locRes = ctx.toolResult("compareRecentLocations", LocationComparison.class);
        ToolResult<MerchantRiskProfile> mrcRes = ctx.toolResult("getMerchantRiskProfile", MerchantRiskProfile.class);
        ToolResult<PriorDisputes> priRes = ctx.toolResult("getPriorDisputes", PriorDisputes.class);
        ToolResult<TransactionVelocity> velRes = ctx.toolResult("getTransactionVelocity", TransactionVelocity.class);
        ToolResult<AmountDeviation> amtRes = ctx.toolResult("checkAmountDeviation", AmountDeviation.class);
        ToolResult<ScreeningResult> scrRes = ctx.toolResult("getSanctionsScreening", ScreeningResult.class);

        boolean txnAvailable = txnRes != null && txnRes.outcome() == ToolOutcome.OK
                && txnRes.data() != null && txnRes.data().found();
        if (!txnAvailable) {
            missing.add("transaction details");
        }

        DeviceSignals dev = devRes != null ? devRes.data() : null;
        LocationComparison loc = locRes != null ? locRes.data() : null;
        MerchantRiskProfile mrc = mrcRes != null ? mrcRes.data() : null;
        PriorDisputes pri = priRes != null ? priRes.data() : null;
        TransactionVelocity vel = velRes != null ? velRes.data() : null;
        AmountDeviation amt = amtRes != null ? amtRes.data() : null;
        ScreeningResult scr = scrRes != null ? scrRes.data() : null;
        if (dev == null) missing.add("device signals");
        if (loc == null) missing.add("location comparison");
        if (mrc == null) missing.add("merchant risk profile");
        if (pri == null) missing.add("prior disputes");
        if (vel == null) missing.add("transaction velocity");
        if (amt == null) missing.add("amount deviation");
        if (scr == null) missing.add("sanctions screening");

        boolean deviceAnomaly = dev != null && (!dev.deviceKnown() || dev.trustLevel() == TrustLevel.LOW);
        boolean locationAnomaly = loc != null && loc.anomalyLevel() == AnomalyLevel.HIGH;
        boolean authAnomaly = txnAvailable
                && (txnRes.data().authOutcome() == AuthOutcome.UNAVAILABLE
                    || txnRes.data().authOutcome() == AuthOutcome.FAILED);
        boolean merchantAnomaly = mrc != null && (mrc.blocklistMatch());
        boolean velocityAnomaly = vel != null && vel.anomalyLevel() == AnomalyLevel.HIGH;
        boolean amountAnomaly = amt != null && amt.anomalyLevel() == AnomalyLevel.HIGH;
        int independentAnomalies = (deviceAnomaly ? 1 : 0) + (locationAnomaly ? 1 : 0)
                + (authAnomaly ? 1 : 0) + (merchantAnomaly ? 1 : 0)
                + (velocityAnomaly ? 1 : 0) + (amountAnomaly ? 1 : 0);

        Boolean recognised = ctx.facts().merchantRecognised();
        boolean denies = Boolean.FALSE.equals(recognised);
        boolean fulfilmentIssue = EnumSet.of(IssueType.FULFILMENT_PROBLEM,
                IssueType.CANCELLATION_REQUEST, IssueType.AMOUNT_DISPUTE)
                .contains(ctx.facts().issueType());

        Classification classification;
        Route route;
        RiskLevel risk;
        double confidence;
        String rationale;

        if (!txnAvailable) {
            applied.add(new RuleApplication("R-002",
                    "Mandatory transaction evidence missing -> INSUFFICIENT_EVIDENCE, manual review",
                    List.of("getTransactionDetails outcome=" + (txnRes == null ? "ABSENT" : txnRes.outcome()))));
            classification = Classification.INSUFFICIENT_EVIDENCE;
            route = Route.MANUAL_REVIEW;
            risk = RiskLevel.MEDIUM;
            confidence = 0.9;
            rationale = "Required transaction evidence is missing or unavailable; routed to manual review.";
        } else if (txnRes.data().duplicateFingerprintMatch()) {
            applied.add(new RuleApplication("R-001",
                    "Duplicate payment fingerprint confirmed -> PAYMENT_OPERATIONS",
                    List.of("duplicateFingerprintMatch=true",
                            "duplicateOfRef=" + txnRes.data().duplicateOfRef())));
            classification = Classification.DUPLICATE_PROCESSING;
            route = Route.PAYMENT_OPERATIONS;
            risk = RiskLevel.MEDIUM;
            confidence = 0.95;
            rationale = "A matching payment fingerprint confirms duplicate processing; routed to payment operations.";
        } else if (scr != null && scr.matchFound()) {
            applied.add(new RuleApplication("R-008",
                    "Sanctions/PEP screening match -> FRAUD_INVESTIGATION for analyst confirmation",
                    List.of("matchFound=true", "listName=" + scr.listName())));
            classification = Classification.UNAUTHORISED_TRANSACTION;
            route = Route.FRAUD_INVESTIGATION;
            risk = RiskLevel.HIGH;
            confidence = 0.85;
            rationale = "Synthetic sanctions/PEP screening matched " + scr.listName()
                    + "; routed to fraud investigation pending analyst confirmation.";
        } else if (denies && independentAnomalies >= 2) {
            applied.add(new RuleApplication("R-004",
                    "Customer denies merchant plus >=2 independent high-severity anomalies -> FRAUD_INVESTIGATION",
                    anomalyFacts(deviceAnomaly, locationAnomaly, authAnomaly, merchantAnomaly,
                            velocityAnomaly, amountAnomaly, pri, recognised)));
            classification = Classification.UNAUTHORISED_TRANSACTION;
            route = Route.FRAUD_INVESTIGATION;
            risk = RiskLevel.HIGH;
            confidence = 0.86;
            rationale = "Customer denies the merchant and multiple independent high-severity anomalies are present; routed to fraud investigation.";
        } else if (independentAnomalies >= 2 || (denies && independentAnomalies >= 1)) {
            applied.add(new RuleApplication("R-005",
                    "High-risk signals present but unauthorised use not established -> FRAUD_INVESTIGATION",
                    anomalyFacts(deviceAnomaly, locationAnomaly, authAnomaly, merchantAnomaly,
                            velocityAnomaly, amountAnomaly, pri, recognised)));
            classification = Classification.SUSPICIOUS_TRANSACTION;
            route = Route.FRAUD_INVESTIGATION;
            risk = RiskLevel.HIGH;
            confidence = 0.68;
            rationale = "Independent high-severity signals exist but the claim does not fully establish unauthorised use; flagged as suspicious for fraud investigation.";
        } else if (Boolean.TRUE.equals(recognised) && fulfilmentIssue) {
            applied.add(new RuleApplication("R-003",
                    "Merchant recognised with fulfilment/cancellation/amount issue -> DISPUTE_OPERATIONS",
                    List.of("merchantRecognised=true", "issueType=" + ctx.facts().issueType())));
            classification = Classification.MERCHANT_DISPUTE;
            route = Route.DISPUTE_OPERATIONS;
            risk = RiskLevel.LOW;
            confidence = 0.8;
            rationale = "Customer recognises the merchant and the issue concerns fulfilment, cancellation or amount; routed to dispute operations.";
        } else if (independentAnomalies == 0) {
            applied.add(new RuleApplication("R-006",
                    "No anomalies and evidence supports authorised activity -> GENUINE_TRANSACTION, manual confirmation",
                    anomalyFacts(deviceAnomaly, locationAnomaly, authAnomaly, merchantAnomaly,
                            velocityAnomaly, amountAnomaly, pri, recognised)));
            classification = Classification.GENUINE_TRANSACTION;
            route = Route.MANUAL_REVIEW;
            risk = RiskLevel.LOW;
            confidence = 0.75;
            rationale = "Evidence supports customer-authorised activity; routed to manual review for confirmation.";
        } else {
            applied.add(new RuleApplication("R-900",
                    "Default: evidence insufficient or contradictory -> INSUFFICIENT_EVIDENCE, manual review",
                    anomalyFacts(deviceAnomaly, locationAnomaly, authAnomaly, merchantAnomaly,
                            velocityAnomaly, amountAnomaly, pri, recognised)));
            classification = Classification.INSUFFICIENT_EVIDENCE;
            route = Route.MANUAL_REVIEW;
            risk = RiskLevel.MEDIUM;
            confidence = 0.6;
            missing.add("conclusive independent signals");
            rationale = "Evidence is insufficient or contradictory; routed to manual review.";
        }

        List<PolicyCitationRef> citations = ctx.policyPassages().stream()
                .map(p -> new PolicyCitationRef(p.documentId(), p.version(), p.section(), p.chunkId(), p.score()))
                .toList();

        if (ctx.policyPassages().isEmpty()) {
            applied.add(new RuleApplication("R-007",
                    "No policy passage met retrieval threshold -> gap identified, route to manual review",
                    List.of("policyPassages=0", "threshold=" + policyRetriever.similarityThreshold())));
            missing.add("approved policy passage (threshold " + policyRetriever.similarityThreshold() + ")");
            route = Route.MANUAL_REVIEW;
            confidence = Math.min(confidence, 0.6);
            rationale += " No approved policy passage met the retrieval threshold; the gap is flagged rather than inventing a procedure.";
        }

        FraudCaseRecommendation result = new FraudCaseRecommendation(
                ctx.caseId(), classification, risk, route, confidence, Boolean.TRUE,
                ctx.evidence().stream().map(com.training.fraudcase.domain.EvidenceItemView::evidenceId).toList(),
                citations, missing,
                applied.stream().map(RuleApplication::ruleId).toList(),
                ctx.toolTraces().stream().map(com.training.fraudcase.domain.ToolTraceView::traceId).toList(),
                rationale, applied);
        return validator.validateAndEnforce(result, ctx);
    }

    @Override
    public String answer(FraudCaseContext ctx, String question) {
        // Deterministic, grounded follow-up: cite stored evidence plus any
        // passages the question itself retrieves. No model, no memory mixing.
        StringBuilder sb = new StringBuilder();
        sb.append("Deterministic answer for case ").append(ctx.caseId()).append(".\n");
        if (!ctx.evidence().isEmpty()) {
            sb.append("Evidence on file:\n");
            ctx.evidence().forEach(e -> sb.append("- [").append(e.evidenceId()).append("] ")
                    .append(e.type()).append('/').append(e.severity()).append(": ")
                    .append(e.valueSummary()).append('\n'));
        }
        List<PolicyPassage> passages = policyRetriever.retrieve(question);
        if (!passages.isEmpty()) {
            sb.append("Relevant policy passages:\n");
            passages.forEach(p -> sb.append("- ").append(p.documentId()).append(" v")
                    .append(p.version()).append(" \"").append(p.section()).append("\" (")
                    .append(p.chunkId()).append(", score ").append(p.score()).append("): ")
                    .append(truncate(p.passage(), 220)).append('\n'));
        } else {
            sb.append("No approved policy passage met the retrieval threshold for this question; ");
            sb.append("flag for manual review rather than assuming a procedure.\n");
        }
        sb.append("All conclusions remain subject to human review.");
        return sb.toString();
    }

    private List<String> anomalyFacts(boolean dev, boolean loc, boolean auth, boolean mrc,
                                      boolean vel, boolean amt, PriorDisputes pri,
                                      Boolean recognised) {
        return List.of(
                "deviceAnomaly=" + dev, "locationAnomaly=" + loc,
                "authAnomaly=" + auth, "merchantAnomaly=" + mrc,
                "velocityAnomaly=" + vel, "amountAnomaly=" + amt,
                "priorSimilarDisputes=" + (pri != null ? pri.similarCount() : "unknown"),
                "merchantRecognised=" + recognised);
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
