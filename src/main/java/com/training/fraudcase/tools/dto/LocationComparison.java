package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.enums.AnomalyLevel;

import java.util.List;

public record LocationComparison(
        double distanceKm,
        long timeDeltaMinutes,
        AnomalyLevel anomalyLevel,
        List<String> observations
) {}
