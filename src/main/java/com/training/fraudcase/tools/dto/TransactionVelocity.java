package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.enums.AnomalyLevel;

import java.util.List;

public record TransactionVelocity(
        int countLast10Min,
        int countLast60Min,
        int baselineDailyCount,
        AnomalyLevel anomalyLevel,
        List<String> observations
) {}
