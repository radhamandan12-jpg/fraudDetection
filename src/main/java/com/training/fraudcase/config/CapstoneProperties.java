package com.training.fraudcase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mode and safety configuration (capstone.*). processor-mode selects the
 * active FraudCaseProcessor: rules | spring-ai | langchain4j.
 */
@ConfigurationProperties(prefix = "capstone")
public class CapstoneProperties {

    public enum ProcessorMode { RULES, SPRING_AI, LANGCHAIN4J }

    private String processorMode = "rules";
    private boolean humanReviewRequired = true;
    private boolean allowFinancialActions = false;
    private boolean allowCustomerContact = false;
    private Retrieval retrieval = new Retrieval();
    private Memory memory = new Memory();
    private Tools tools = new Tools();
    private Langchain4j langchain4j = new Langchain4j();

    public ProcessorMode parsedProcessorMode() {
        return switch (processorMode.trim().toLowerCase().replace("-", "_")) {
            case "spring_ai" -> ProcessorMode.SPRING_AI;
            case "langchain4j" -> ProcessorMode.LANGCHAIN4J;
            default -> ProcessorMode.RULES;
        };
    }

    public String getProcessorMode() { return processorMode; }
    public void setProcessorMode(String processorMode) { this.processorMode = processorMode; }
    public boolean isHumanReviewRequired() { return humanReviewRequired; }
    public void setHumanReviewRequired(boolean humanReviewRequired) { this.humanReviewRequired = humanReviewRequired; }
    public boolean isAllowFinancialActions() { return allowFinancialActions; }
    public void setAllowFinancialActions(boolean allowFinancialActions) { this.allowFinancialActions = allowFinancialActions; }
    public boolean isAllowCustomerContact() { return allowCustomerContact; }
    public void setAllowCustomerContact(boolean allowCustomerContact) { this.allowCustomerContact = allowCustomerContact; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public Tools getTools() { return tools; }
    public void setTools(Tools tools) { this.tools = tools; }
    public Langchain4j getLangchain4j() { return langchain4j; }
    public void setLangchain4j(Langchain4j langchain4j) { this.langchain4j = langchain4j; }

    public static class Retrieval {
        private int topK = 4;
        private double similarityThreshold = 0.35;
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    }

    public static class Memory {
        private int maxMessages = 12;
        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    }

    public static class Tools {
        private long timeoutMs = 2500;
        private String deterministicSeed = "fraud-training-v1";
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getDeterministicSeed() { return deterministicSeed; }
        public void setDeterministicSeed(String deterministicSeed) { this.deterministicSeed = deterministicSeed; }
    }

    public static class Langchain4j {
        private String apiKey = "not-configured";
        private String baseUrl = "https://api.anthropic.com";
        private String model = "claude-sonnet-4-5";
        private double temperature = 0.2;
        private int maxTokens = 2048;
        private int timeoutSeconds = 60;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
