package com.training.fraudcase.application;

import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.InvestigationPlan;
import com.training.fraudcase.domain.enums.IssueType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Plan stage: classifies the investigation goal and produces a bounded
 * evidence plan — only allow-listed tools, only what the case needs.
 */
@Component
public class InvestigationPlanner {

    private static final List<String> EVIDENCE_TOOLS = List.of(
            "getTransactionDetails", "getDeviceSignals", "compareRecentLocations",
            "getMerchantRiskProfile", "getPriorDisputes");

    public InvestigationPlan plan(CaseFacts facts) {
        String goal = switch (facts.issueType()) {
            case UNRECOGNISED_TRANSACTION ->
                    "Determine whether this unrecognised card transaction is unauthorised use, "
                            + "a merchant/billing dispute, duplicate processing, or insufficiently evidenced. "
                            + "Recommend a route; take no automated action.";
            case DUPLICATE_CHARGE ->
                    "Determine whether this transaction is duplicate processing versus authorised "
                            + "or insufficiently evidenced. Recommend a route; take no automated action.";
            case FULFILMENT_PROBLEM, CANCELLATION_REQUEST, AMOUNT_DISPUTE ->
                    "Determine whether this recognised-merchant issue is a merchant/billing dispute "
                            + "or requires another route. Recommend a route; take no automated action.";
            case OTHER ->
                    "Classify this disputed transaction against the approved taxonomy and recommend "
                            + "a route; take no automated action.";
        };
        String query = retrievalQuery(facts);
        return new InvestigationPlan(goal, EVIDENCE_TOOLS, query);
    }

    private String retrievalQuery(CaseFacts f) {
        StringBuilder q = new StringBuilder();
        switch (f.issueType()) {
            case UNRECOGNISED_TRANSACTION -> q.append("unauthorised card not present transaction ")
                    .append("new device unusual location authentication customer denies merchant");
            case DUPLICATE_CHARGE -> q.append("duplicate transaction processing same merchant amount fingerprint");
            case FULFILMENT_PROBLEM -> q.append("merchant dispute goods services not received fulfilment");
            case CANCELLATION_REQUEST -> q.append("merchant dispute cancellation refund recurring");
            case AMOUNT_DISPUTE -> q.append("merchant dispute incorrect amount charged billing");
            case OTHER -> q.append("dispute triage classification manual review procedure");
        }
        q.append(" ").append(f.transactionChannel());
        return q.toString();
    }
}
