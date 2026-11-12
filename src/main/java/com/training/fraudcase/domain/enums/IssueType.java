package com.training.fraudcase.domain.enums;

/** Customer-reported issue type captured at intake. */
public enum IssueType {
    UNRECOGNISED_TRANSACTION,
    FULFILMENT_PROBLEM,
    CANCELLATION_REQUEST,
    AMOUNT_DISPUTE,
    DUPLICATE_CHARGE,
    OTHER
}
