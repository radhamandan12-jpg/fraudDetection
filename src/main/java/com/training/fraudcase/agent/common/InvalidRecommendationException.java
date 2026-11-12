package com.training.fraudcase.agent.common;

/** Model output failed schema/guardrail validation — fails safely to
 *  incomplete/manual review upstream. */
public class InvalidRecommendationException extends RuntimeException {
    public InvalidRecommendationException(String message) {
        super(message);
    }
}
