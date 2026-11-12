package com.training.fraudcase;

import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.agent.springai.SpringAiFraudCaseAgent;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.tools.EvidenceTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static com.training.fraudcase.TestFixtures.ctx;
import static com.training.fraudcase.TestFixtures.facts;
import static com.training.fraudcase.TestFixtures.passage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract test for the Spring AI adapter driven by a stub ChatModel —
 * verifies the typed .entity() mapping and that the application validator
 * still rejects fabricated references the model invents.
 */
class SpringAiAdapterContractTest {

    private final FraudCaseContext context = ctx(
            facts("CASE-2026-0001", "TXN-FRAUD-1", IssueType.UNRECOGNISED_TRANSACTION, false),
            List.of(), List.of(passage()), List.of());

    private SpringAiFraudCaseAgent agentReturning(String modelJson) throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(modelJson)))));
        ChatClient client = ChatClient.builder(model).build();
        return new SpringAiFraudCaseAgent(client, mock(EvidenceTools.class),
                new RecommendationValidator(),
                new ClassPathResource("prompts/fraud-system.txt"),
                new ClassPathResource("prompts/fraud-user.txt"));
    }

    private static String json(String evidenceIds, String traceIds) {
        return """
                {"caseId":"CASE-2026-0001","classification":"UNAUTHORISED_TRANSACTION",
                "riskLevel":"HIGH","recommendedRoute":"FRAUD_INVESTIGATION","confidence":0.9,
                "humanReviewRequired":true,"evidenceIds":[%s],"policyCitations":[],
                "missingInformation":[],"ruleIds":["R-004"],"toolTraceIds":[%s],
                "rationale":"stub model rationale","appliedRules":[]}
                """.formatted(evidenceIds, traceIds);
    }

    @Test
    void validModelOutputPassesValidation() throws Exception {
        FraudCaseRecommendation r = agentReturning(json("\"EV-TEST001\"", "")).analyze(context);
        assertEquals("CASE-2026-0001", r.caseId());
        assertTrue(r.humanReviewRequired());
    }

    @Test
    void fabricatedToolTraceIdsAreRejected() throws Exception {
        var agent = agentReturning(json("\"EV-TEST001\"", "\"TR-getTransactionDetails\""));
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }

    @Test
    void fabricatedEvidenceIdsAreRejected() throws Exception {
        var agent = agentReturning(json("\"EV-INVENTED-99\"", ""));
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }

    @Test
    void malformedModelOutputFailsSafely() throws Exception {
        var agent = agentReturning("not json at all");
        assertThrows(InvalidRecommendationException.class, () -> agent.analyze(context));
    }
}
