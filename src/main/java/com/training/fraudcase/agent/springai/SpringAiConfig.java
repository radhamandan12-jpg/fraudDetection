package com.training.fraudcase.agent.springai;

import com.training.fraudcase.config.CapstoneProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI wiring, active only when capstone.processor-mode=spring-ai.
 * Case-keyed chat memory is bounded by capstone.memory.max-messages and
 * never shared across case IDs.
 */
@Configuration
@ConditionalOnProperty(name = "capstone.processor-mode", havingValue = "spring-ai")
public class SpringAiConfig {

    @Bean
    ChatMemory fraudCaseChatMemory(CapstoneProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(props.getMemory().getMaxMessages())
                .build();
    }

    @Bean
    MessageChatMemoryAdvisor fraudCaseMemoryAdvisor(ChatMemory fraudCaseChatMemory) {
        return MessageChatMemoryAdvisor.builder(fraudCaseChatMemory).build();
    }

    @Bean
    ChatClient fraudCaseChatClient(ChatModel chatModel, MessageChatMemoryAdvisor fraudCaseMemoryAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(fraudCaseMemoryAdvisor)
                .build();
    }
}
