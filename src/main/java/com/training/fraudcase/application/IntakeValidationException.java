package com.training.fraudcase.application;

import java.util.List;

public class IntakeValidationException extends RuntimeException {
    private final List<String> problems;

    public IntakeValidationException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = problems;
    }

    public List<String> problems() {
        return problems;
    }
}
