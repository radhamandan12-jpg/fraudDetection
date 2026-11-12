package com.training.fraudcase.domain;

/** Thrown when a case state transition is not permitted. */
public class CaseStateException extends RuntimeException {
    public CaseStateException(String message) {
        super(message);
    }
}
