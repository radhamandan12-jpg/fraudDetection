package com.training.fraudcase.agent.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.fraudcase.agent.common.FraudCaseContext;
import com.training.fraudcase.agent.common.FraudCaseProcessor;
import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.agent.common.InvalidRecommendationException;
import com.training.fraudcase.agent.common.RecommendationValidator;
import com.training.fraudcase.tools.CaseToolContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LangChain4j adapter (§6.4). Explicit JSON-to-DTO mapping, then the same
 * application validation as every other mode. Model errors fail safely to
 * incomplete/manual review upstream.
 */
@Component
@ConditionalOnProperty(name = "capstone.processor-mode", havingValue = "langchain4j")
public class LangChain4jFraudCaseAgent implements FraudCaseProcessor {

    private final FraudInvestigationService service;
    private final RecommendationValidator validator;
    private final ObjectMapper objectMapper;

    public LangChain4jFraudCaseAgent(FraudInvestigationService service,
                                     RecommendationValidator validator,
                                     ObjectMapper objectMapper) {
        this.service = service;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String mode() {
        return "langchain4j";
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        try {
            CaseToolContext.set(context.caseId());
            String json = stripFences(service.analyze(context.caseId(), context.renderPrompt()));
            FraudCaseRecommendation result = objectMapper.readValue(json, FraudCaseRecommendation.class);
            return validator.validateAndEnforce(result, context);
        } catch (InvalidRecommendationException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRecommendationException("langchain4j analysis failed safely: "
                    + e.getClass().getSimpleName());
        } finally {
            CaseToolContext.clear();
        }
    }

    @Override
    public String answer(FraudCaseContext context, String question) {
        try {
            CaseToolContext.set(context.caseId());
            return service.answer(context.caseId(),
                    context.renderCaseSummary(), context.renderToolEvidence(),
                    context.renderPolicyContext(), question);
        } finally {
            CaseToolContext.clear();
        }
    }

    private static String stripFences(String raw) {
        if (raw == null) {
            throw new InvalidRecommendationException("empty model response");
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s.trim();
    }
}
