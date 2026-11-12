package com.training.fraudcase.agent.langchain4j;

import com.training.fraudcase.config.CapstoneProperties;
import com.training.fraudcase.knowledge.PolicyRetriever;
import com.training.fraudcase.tools.EvidenceTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j wiring, active only when capstone.processor-mode=langchain4j.
 * The AI Service is built with model, allow-listed tools, case-keyed
 * bounded memory and retrieval augmentation — matching the Spring AI
 * adapter's contract.
 */
@Configuration
@ConditionalOnProperty(name = "capstone.processor-mode", havingValue = "langchain4j")
public class LangChain4jConfig {

    @Bean
    AnthropicChatModel langchain4jChatModel(CapstoneProperties props) {
        var lc = props.getLangchain4j();
        return AnthropicChatModel.builder()
                .apiKey(lc.getApiKey())
                .baseUrl(lc.getBaseUrl())
                .modelName(lc.getModel())
                .temperature(lc.getTemperature())
                .maxTokens(lc.getMaxTokens())
                .timeout(Duration.ofSeconds(lc.getTimeoutSeconds()))
                .build();
    }

    @Bean
    ChatMemoryProvider fraudCaseMemoryProvider(CapstoneProperties props) {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        int maxMessages = props.getMemory().getMaxMessages();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(store)
                .build();
    }

    @Bean
    PolicyContentRetriever policyContentRetriever(PolicyRetriever retriever) {
        return new PolicyContentRetriever(retriever);
    }

    @Bean
    FraudInvestigationService fraudInvestigationService(AnthropicChatModel model,
                                                        EvidenceTools evidenceTools,
                                                        ChatMemoryProvider memoryProvider,
                                                        PolicyContentRetriever contentRetriever) {
        return AiServices.builder(FraudInvestigationService.class)
                .chatModel(model)
                .tools(evidenceTools)
                // Never leak internals to the model/provider: argument errors
                // fail the call; execution errors are only surfaced when a
                // tool explicitly marks them visible (ours return structured
                // failures instead of throwing).
                .toolArgumentsErrorHandler(
                        dev.langchain4j.service.tool.ToolArgumentsErrorHandler.failInvocation())
                .toolExecutionErrorHandler(
                        dev.langchain4j.service.tool.ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
                .chatMemoryProvider(memoryProvider)
                .contentRetriever(contentRetriever)
                .build();
    }
}
