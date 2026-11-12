package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.enums.RiskBand;

import java.util.List;

public record MerchantRiskProfile(
        RiskBand riskBand,
        boolean blocklistMatch,
        String category,
        List<String> observations
) {}
