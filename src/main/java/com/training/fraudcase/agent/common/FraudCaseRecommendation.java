package com.training.fraudcase.agent.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.domain.enums.RiskLevel;
import com.training.fraudcase.domain.enums.Route;

import java.util.List;

/**
 * The single agreed output schema (§3.3), parsed into a typed DTO and
 * validated before persistence or display.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FraudCaseRecommendation(
        String caseId,
        Classification classification,
        RiskLevel riskLevel,
        Route recommendedRoute,
        Double confidence,
        Boolean humanReviewRequired,
        List<String> evidenceIds,
        List<PolicyCitationRef> policyCitations,
        List<String> missingInformation,
        List<String> ruleIds,
        List<String> toolTraceIds,
        String rationale,
        List<RuleApplication> appliedRules
) {
    public FraudCaseRecommendation {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        policyCitations = policyCitations == null ? List.of() : List.copyOf(policyCitations);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
        toolTraceIds = toolTraceIds == null ? List.of() : List.copyOf(toolTraceIds);
        appliedRules = appliedRules == null ? List.of() : List.copyOf(appliedRules);
    }
}
