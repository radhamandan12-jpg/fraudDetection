package com.training.fraudcase.agent.common;

import java.util.List;

/**
 * One applied deterministic rule: its ID and the exact input facts used —
 * mandatory for every deterministic result.
 */
public record RuleApplication(
        String ruleId,
        String description,
        List<String> inputFacts
) {}
