package com.training.fraudcase.agent.common;

import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.ChatTurn;
import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.domain.ToolTraceView;
import com.training.fraudcase.tools.ToolResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything a processor may use: validated intake facts, tool evidence,
 * retrieved policy passages, tool traces and case-scoped conversation —
 * nothing else.
 */
public record FraudCaseContext(
        String caseId,
        String investigationGoal,
        CaseFacts facts,
        List<EvidenceItemView> evidence,
        List<PolicyPassage> policyPassages,
        List<ToolTraceView> toolTraces,
        List<ChatTurn> conversation,
        List<ToolResult<?>> toolResults
) {
    public FraudCaseContext(String caseId, String investigationGoal, CaseFacts facts,
                            List<EvidenceItemView> evidence, List<PolicyPassage> policyPassages,
                            List<ToolTraceView> toolTraces, List<ChatTurn> conversation) {
        this(caseId, investigationGoal, facts, evidence, policyPassages,
                toolTraces, conversation, List.of());
    }

    /** First tool result of a given name and payload type, if present. */
    @SuppressWarnings("unchecked")
    public <T> ToolResult<T> toolResult(String toolName, Class<T> dataType) {
        return (ToolResult<T>) toolResults.stream()
                .filter(r -> r.toolName().equals(toolName))
                .filter(r -> r.data() != null && dataType.isInstance(r.data()))
                .findFirst().orElse(null);
    }
    /** Template variables per the prompt contract (§6.2). */
    public Map<String, Object> promptVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("caseSummary", renderCaseSummary());
        vars.put("investigationGoal", investigationGoal);
        vars.put("toolEvidence", renderToolEvidence());
        vars.put("policyContext", renderPolicyContext());
        vars.put("conversationContext", renderConversation());
        return vars;
    }

    /** Single rendered user prompt for adapters that take plain text (LC4J). */
    public String renderPrompt() {
        return "INVESTIGATION GOAL:\n" + investigationGoal + "\n\n"
                + "CASE FACTS:\n" + renderCaseSummary() + "\n\n"
                + "TOOL EVIDENCE:\n" + renderToolEvidence() + "\n\n"
                + "RETRIEVED POLICY PASSAGES:\n" + renderPolicyContext() + "\n\n"
                + "CONVERSATION CONTEXT:\n" + renderConversation() + "\n\n"
                + "Return only the agreed JSON schema.";
    }

    public String renderCaseSummary() {
        CaseFacts f = facts;
        return "caseId=" + f.caseId()
                + "\nissueType=" + f.issueType()
                + "\nreportedAt=" + f.reportedAt() + " channel=" + f.caseChannel()
                + "\nmaskedCustomerRef=" + f.maskedCustomerRef() + " homeRegion=" + f.homeRegion()
                + "\ntransactionRef=" + f.transactionRef()
                + "\namount=" + f.amount() + " " + f.currency()
                + "\noccurredAt=" + f.occurredAt()
                + "\ntransactionStatus=" + f.transactionStatus() + " transactionChannel=" + f.transactionChannel()
                + "\nmaskedMerchant=" + f.maskedMerchant()
                + "\ncardPossession=" + f.cardPossession()
                + "\nmerchantRecognised=" + f.merchantRecognised()
                + "\ncustomerStatement=\"" + f.customerStatement() + "\"";
    }

    public String renderToolEvidence() {
        if (evidence.isEmpty()) {
            return "(no tool evidence collected)";
        }
        return evidence.stream()
                .map(e -> "- [" + e.evidenceId() + "] " + e.type() + "/" + e.severity()
                        + " via " + (e.sourceTool() == null ? "?" : e.sourceTool())
                        + ": " + e.valueSummary())
                .collect(Collectors.joining("\n"));
    }

    public String renderPolicyContext() {
        if (policyPassages.isEmpty()) {
            return "(no policy passages met the retrieval threshold — do not invent a procedure)";
        }
        return policyPassages.stream()
                .map(p -> "- [" + p.chunkId() + "] doc=" + p.documentId() + " v" + p.version()
                        + " section=\"" + p.section() + "\" score=" + p.score()
                        + "\n  " + p.passage().replace('\n', ' '))
                .collect(Collectors.joining("\n"));
    }

    public String renderConversation() {
        if (conversation.isEmpty()) {
            return "(no prior conversation)";
        }
        return conversation.stream()
                .map(t -> t.role() + ": " + t.content())
                .collect(Collectors.joining("\n"));
    }
}
