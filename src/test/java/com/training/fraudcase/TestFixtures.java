package com.training.fraudcase;

import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.config.CapstoneProperties;
import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.domain.enums.CardPossession;
import com.training.fraudcase.domain.enums.CaseChannel;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.Provenance;
import com.training.fraudcase.domain.enums.EvidenceType;
import com.training.fraudcase.domain.enums.Severity;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;
import com.training.fraudcase.domain.enums.ToolOutcome;
import com.training.fraudcase.knowledge.PolicyRetriever;
import com.training.fraudcase.tools.SyntheticEvidenceAdapter;
import com.training.fraudcase.tools.ToolResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared builders for unit tests — deterministic, no Spring context. */
public final class TestFixtures {

    private TestFixtures() {}

    public static CapstoneProperties props() {
        return new CapstoneProperties();
    }

    public static SyntheticEvidenceAdapter adapter() {
        return new SyntheticEvidenceAdapter(props());
    }

    public static CaseFacts facts(String caseId, String txnRef, IssueType issue, Boolean recognised) {
        return facts(caseId, txnRef, issue, recognised, "CUST-8842", "MRC-UNRECOGNISED-77");
    }

    public static CaseFacts facts(String caseId, String txnRef, IssueType issue, Boolean recognised,
                                  String customerRef, String merchant) {
        return new CaseFacts(caseId, Instant.now(), CaseChannel.WEB, "analyst-1",
                customerRef, "EU-IE", txnRef, new BigDecimal("129.99"), "EUR", Instant.now(),
                TransactionStatus.POSTED, TransactionChannel.CARD_NOT_PRESENT,
                merchant, issue, "I did not make this purchase.",
                CardPossession.IN_POSSESSION, recognised);
    }

    public static <T> ToolResult<T> ok(String tool, T data) {
        return new ToolResult<>("TR-TEST01", tool, ToolOutcome.OK, data, "ok",
                Instant.now(), "TEST");
    }

    public static <T> ToolResult<T> unavailable(String tool) {
        return ToolResult.unavailable(tool, "TR-TEST02", "synthetic outage");
    }

    public static PolicyRetriever retrieverWith(List<PolicyPassage> passages, double threshold) {
        return new PolicyRetriever() {
            @Override public List<PolicyPassage> retrieve(String query) { return passages; }
            @Override public double similarityThreshold() { return threshold; }
            @Override public int topK() { return 4; }
        };
    }

    public static PolicyPassage passage() {
        return new PolicyPassage("POL-CNP-017", "3.2", "3.2 Unauthorised indicators",
                "pol-cnp-017-c002", 0.89, "Two or more independent high-severity indicators...");
    }

    public static FraudCaseContext ctx(CaseFacts facts,
                                       List<EvidenceItemView> evidence,
                                       List<PolicyPassage> passages,
                                       List<ToolResult<?>> results) {
        if (evidence.isEmpty()) {
            evidence = List.of(new EvidenceItemView("EV-TEST001", EvidenceType.CUSTOMER_STATEMENT,
                    Severity.INFO, "intake", Provenance.USER_INTAKE, Instant.now(), null));
        }
        return new FraudCaseContext(facts.caseId(), "test goal", facts, evidence, passages,
                List.of(), List.of(), results);
    }
}
