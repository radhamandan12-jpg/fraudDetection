package com.training.fraudcase.domain.enums;

/** Typed classification from the agreed output schema. */
public enum Classification {
    UNAUTHORISED_TRANSACTION,
    MERCHANT_DISPUTE,
    DUPLICATE_PROCESSING,
    SUSPICIOUS_TRANSACTION,
    INSUFFICIENT_EVIDENCE,
    GENUINE_TRANSACTION
}
