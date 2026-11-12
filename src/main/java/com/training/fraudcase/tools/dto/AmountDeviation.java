package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.enums.AnomalyLevel;

import java.math.BigDecimal;
import java.util.List;

public record AmountDeviation(
        BigDecimal ratioVsBaseline,
        BigDecimal baselineMedian,
        AnomalyLevel anomalyLevel,
        List<String> observations
) {}
