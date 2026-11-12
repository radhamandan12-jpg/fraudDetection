package com.training.fraudcase.application;

import com.training.fraudcase.agent.common.FraudCaseRecommendation;
import com.training.fraudcase.domain.InvestigationPlan;
import com.training.fraudcase.domain.enums.CaseStatus;

/** Result of one bounded investigation run. */
public record InvestigationResult(
        CaseStatus status,
        String mode,
        InvestigationPlan plan,
        FraudCaseRecommendation recommendation,
        String note
) {}
