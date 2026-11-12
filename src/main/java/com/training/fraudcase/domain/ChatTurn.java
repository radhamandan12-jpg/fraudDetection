package com.training.fraudcase.domain;

import java.time.Instant;

/** One turn in a case-scoped follow-up conversation. */
public record ChatTurn(String role, String content, Instant at) {

    public static ChatTurn user(String content) {
        return new ChatTurn("user", content, Instant.now());
    }

    public static ChatTurn assistant(String content) {
        return new ChatTurn("assistant", content, Instant.now());
    }
}
