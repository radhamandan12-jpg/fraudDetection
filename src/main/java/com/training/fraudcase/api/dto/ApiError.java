package com.training.fraudcase.api.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(String error, List<String> details, Instant timestamp) {

    public static ApiError of(String error, String... details) {
        return new ApiError(error, List.of(details), Instant.now());
    }
}
