package com.training.fraudcase.api.dto;

import com.training.fraudcase.domain.enums.CaseStatus;
import com.training.fraudcase.domain.enums.IssueType;

import java.math.BigDecimal;
import java.time.Instant;

public record CaseSummaryView(
        String caseId,
        CaseStatus status,
        String maskedCustomerRef,
        String maskedMerchant,
        IssueType issueType,
        BigDecimal amount,
        String currency,
        Instant reportedAt,
        String createdBy
) {}
