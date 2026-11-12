package com.training.fraudcase.domain;

import com.training.fraudcase.domain.enums.CardPossession;
import com.training.fraudcase.domain.enums.CaseChannel;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Validated intake snapshot — framework-neutral fact carrier passed to
 * every processor. Contains only masked/tokenised values; raw account or
 * card numbers are rejected before this object is built.
 */
public record CaseFacts(
        String caseId,
        Instant reportedAt,
        CaseChannel caseChannel,
        String analystId,
        String maskedCustomerRef,
        String homeRegion,
        String transactionRef,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        TransactionStatus transactionStatus,
        TransactionChannel transactionChannel,
        String maskedMerchant,
        IssueType issueType,
        String customerStatement,
        CardPossession cardPossession,
        Boolean merchantRecognised
) {}
