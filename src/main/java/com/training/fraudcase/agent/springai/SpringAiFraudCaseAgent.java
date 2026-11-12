package com.training.fraudcase.agent.springai;

import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseProcessor;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.tools.CaseToolContext;
import com.training.fraudcase.tools.EvidenceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Spring AI adapter (§6.3). ChatClient with the allow-listed tools and a
 * case-keyed memory advisor; policy passages are injected through the
 * retriever-backed prompt variable so citations stay exact. Typed output
 * via .entity() then application validation — invalid output fails safely.
 */
@Component
@ConditionalOnProperty(name = "capstone.processor-mode", havingValue = "spring-ai")
public class SpringAiFraudCaseAgent implements FraudCaseProcessor {

    private final ChatClient chatClient;
    private final EvidenceTools evidenceTools;
    private final RecommendationValidator validator;
    private final String systemPrompt;
    private final String userTemplate;

    public SpringAiFraudCaseAgent(ChatClient fraudCaseChatClient,
                                EvidenceTools evidenceTools,
                                RecommendationValidator validator,
                                @Value("classpath:prompts/fraud-system.txt") Resource systemRes,
                                @Value("classpath:prompts/fraud-user.txt") Resource userRes) throws IOException {
        this.chatClient = fraudCaseChatClient;
        this.evidenceTools = evidenceTools;
        this.validator = validator;
        this.systemPrompt = StreamUtils.copyToString(systemRes.getInputStream(), StandardCharsets.UTF_8);
        this.userTemplate = StreamUtils.copyToString(userRes.getInputStream(), StandardCharsets.UTF_8);
    }

    @Override
    public String mode() {
        return "spring-ai";
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        try {
            CaseToolContext.set(context.caseId());
            Map<String, Object> vars = context.promptVariables();
            FraudCaseRecommendation result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userTemplate).params(vars))
                    .tools(evidenceTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.caseId()))
                    .call()
                    .entity(FraudCaseRecommendation.class);
            return validator.validateAndEnforce(result, context);
        } catch (InvalidRecommendationException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRecommendationException("spring-ai analysis failed safely: "
                    + e.getClass().getSimpleName());
        } finally {
            CaseToolContext.clear();
        }
    }

    @Override
    public String answer(FraudCaseContext context, String question) {
        try {
            CaseToolContext.set(context.caseId());
            Map<String, Object> vars = context.promptVariables();
            vars.put("question", question);
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text("Use the validated case facts, tool evidence and retrieved policy passages below to answer the follow-up question. "
                                    + "Cite evidence IDs and policy citations exactly; if context is absent, say so rather than inventing.\n\n"
                                    + "CASE FACTS:\n{caseSummary}\n\nTOOL EVIDENCE:\n{toolEvidence}\n\nPOLICY CONTEXT:\n{policyContext}\n\nQUESTION: {question}")
                            .params(vars))
                    .tools(evidenceTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.caseId()))
                    .call()
                    .content();
        } finally {
            CaseToolContext.clear();
        }
    }
}
