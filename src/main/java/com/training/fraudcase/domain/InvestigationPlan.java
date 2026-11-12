package com.training.fraudcase.domain;

import java.util.List;

/**
 * Bounded evidence plan produced in the Plan stage: a classified
 * investigation goal plus the allow-listed tools that will run.
 */
public record InvestigationPlan(
        String goal,
        List<String> plannedTools,
        String retrievalQuery
) {}
