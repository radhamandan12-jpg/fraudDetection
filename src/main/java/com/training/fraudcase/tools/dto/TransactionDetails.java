package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.enums.AuthOutcome;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDetails(
        boolean found,
        TransactionStatus status,
        Instant timestamp,
        BigDecimal amount,
        String currency,
        TransactionChannel channel,
        AuthOutcome authOutcome,
        String maskedMerchant,
        boolean duplicateFingerprintMatch,
        String duplicateOfRef
) {}
