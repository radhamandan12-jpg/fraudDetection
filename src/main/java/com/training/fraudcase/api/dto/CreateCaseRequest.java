package com.training.fraudcase.api.dto;

import com.training.fraudcase.domain.enums.CardPossession;
import com.training.fraudcase.domain.enums.CaseChannel;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Intake payload (§2.4). Masked/tokenised values only — full account or
 * card numbers are rejected in service-level validation.
 */
public record CreateCaseRequest(

        @NotNull Instant reportedAt,
        @NotNull CaseChannel channel,

        @NotBlank @Size(max = 128) String maskedCustomerRef,
        @Size(max = 64) String homeRegion,

        @NotBlank @Size(max = 128) String transactionRef,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "ISO 4217 currency") String currency,
        @NotNull Instant occurredAt,
        @NotNull TransactionStatus transactionStatus,
        @NotNull TransactionChannel transactionChannel,
        @NotBlank @Size(max = 128) String maskedMerchant,

        @NotNull IssueType issueType,
        @NotBlank @Size(max = 4000) String customerStatement,
        @NotNull CardPossession cardPossession,
        @NotNull Boolean merchantRecognised,

        @AssertTrue(message = "synthetic data must be confirmed") boolean syntheticDataConfirmed,
        @AssertTrue(message = "human review must be required") boolean reviewRequired
) {}
