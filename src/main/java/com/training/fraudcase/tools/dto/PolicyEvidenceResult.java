package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.PolicyPassage;

import java.util.List;

public record PolicyEvidenceResult(
        String query,
        int matchCount,
        List<PolicyPassage> passages
) {}
