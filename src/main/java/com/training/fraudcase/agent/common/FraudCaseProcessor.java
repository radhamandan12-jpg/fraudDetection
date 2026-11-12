package com.training.fraudcase.agent.common;

/**
 * Common processor contract — implemented by the deterministic rules
 * adapter, the Spring AI adapter and the LangChain4j adapter. Selected
 * purely through capstone.processor-mode.
 */
public interface FraudCaseProcessor {

    /** Produce a typed, schema-valid recommendation for the case. */
    FraudCaseRecommendation analyze(FraudCaseContext context);

    /** Answer a case-scoped follow-up question using the same evidence and
     *  citation model — never bypasses recommendation validation elsewhere. */
    String answer(FraudCaseContext context, String question);

    /** Configuration key for this processor: rules | spring-ai | langchain4j */
    String mode();
}
