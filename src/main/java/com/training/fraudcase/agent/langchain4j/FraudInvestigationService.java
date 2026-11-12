package com.training.fraudcase.agent.langchain4j;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j AI Service (§6.4). caseId is the memory identifier —
 * conversations can never mix across cases. Returns raw JSON text which
 * the adapter maps to the typed DTO and validates.
 */
public interface FraudInvestigationService {

    @SystemMessage(fromResource = "prompts/fraud-system.txt")
    String analyze(@MemoryId String caseId, @UserMessage String casePrompt);

    @SystemMessage(fromResource = "prompts/fraud-system.txt")
    @UserMessage("""
            Use the validated case facts, tool evidence and retrieved policy passages below to answer
            the follow-up question. Cite evidence IDs and policy citations exactly; if context is
            absent, say so rather than inventing.

            CASE FACTS:
            {{caseSummary}}

            TOOL EVIDENCE:
            {{toolEvidence}}

            POLICY CONTEXT:
            {{policyContext}}

            QUESTION: {{question}}
            """)
    String answer(@MemoryId String caseId,
                  @V("caseSummary") String caseSummary,
                  @V("toolEvidence") String toolEvidence,
                  @V("policyContext") String policyContext,
                  @V("question") String question);
}
