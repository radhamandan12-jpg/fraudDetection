package com.training.fraudcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.agent.langchain4j.FraudInvestigationService;
import com.training.fraudcase.agent.langchain4j.LangChain4jFraudCaseAgent;
import com.training.fraudcase.domain.enums.IssueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static com.training.fraudcase.TestFixtures.ctx;
import static com.training.fraudcase.TestFixtures.facts;
import static com.training.fraudcase.TestFixtures.passage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the LangChain4j adapter with a stub model — verifies
 * JSON-to-DTO mapping, fence stripping, schema validation and the
 * referential-integrity guard that rejects fabricated IDs.
 */
class LangChain4jAdapterContractTest {

    private final FraudCaseContext context = ctx(
            facts("CASE-2026-0001", "TXN-FRAUD-1", IssueType.UNRECOGNISED_TRANSACTION, false),
            List.of(), List.of(passage()), List.of());

    private LangChain4jFraudCaseAgent agentWith(Supplier<String> modelJson) {
        FraudInvestigationService stub = new FraudInvestigationService() {
            @Override
            public String analyze(String caseId, String casePrompt) {
                return modelJson.get();
            }

            @Override
            public String answer(String caseId, String caseSummary, String toolEvidence,
                                 String policyContext, String question) {
                return "stub answer";
            }
        };
        return new LangChain4jFraudCaseAgent(stub, new RecommendationValidator(), new ObjectMapper());
    }

    private static String json(String evidenceIds, String traceIds, String citations) {
        return """
                {"caseId":"CASE-2026-0001","classification":"UNAUTHORISED_TRANSACTION",
                "riskLevel":"HIGH","recommendedRoute":"FRAUD_INVESTIGATION","confidence":0.9,
                "humanReviewRequired":true,"evidenceIds":[%s],"policyCitations":[%s],
                "missingInformation":[],"ruleIds":["R-004"],"toolTraceIds":[%s],
                "rationale":"stub model rationale","appliedRules":[]}
                """.formatted(evidenceIds, citations, traceIds);
    }

    @Test
    void validModelOutputPassesValidation() {
        var agent = agentWith(() -> json("\"EV-TEST001\"", "", ""));
        FraudCaseRecommendation r = agent.analyze(context);
        assertEquals("CASE-2026-0001", r.caseId());
        assertTrue(r.humanReviewRequired());
    }

    @Test
    void fencedJsonIsStrippedAndParsed() {
        var agent = agentWith(() -> "```json\n" + json("\"EV-TEST001\"", "", "") + "\n```");
        FraudCaseRecommendation r = agent.analyze(context);
        assertEquals("CASE-2026-0001", r.caseId());
    }

    @Test
    void fabricatedToolTraceIdsAreRejected() {
        var agent = agentWith(() -> json("\"EV-TEST001\"", "\"TR-getTransactionDetails\"", ""));
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }

    @Test
    void fabricatedEvidenceIdsAreRejected() {
        var agent = agentWith(() -> json("\"EV-INVENTED-99\"", "", ""));
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }

    @Test
    void citationOutsideRetrievedContextIsRejected() {
        String citation = """
                {"documentId":"POL-FAKE-1","version":"1.0","section":"s","chunkId":"fake-c001","score":0.9}
                """;
        var agent = agentWith(() -> json("\"EV-TEST001\"", "", citation));
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }

    @Test
    void emptyModelResponseFailsSafely() {
        var agent = agentWith(() -> "");
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }

    @Test
    void malformedJsonFailsSafely() {
        var agent = agentWith(() -> "not json at all");
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }
}
