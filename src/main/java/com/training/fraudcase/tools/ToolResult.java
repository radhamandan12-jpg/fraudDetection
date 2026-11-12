package com.training.fraudcase.tools;

import com.training.fraudcase.domain.enums.ToolOutcome;

import java.time.Instant;

/**
 * Structured tool result shared by all modes: provenance fields
 * (toolResultId, observedAt, origin) plus the typed payload.
 */
public record ToolResult<T>(
        String toolResultId,
        String toolName,
        ToolOutcome outcome,
        T data,
        String summary,
        Instant observedAt,
        String origin
) {
    public static <T> ToolResult<T> unavailable(String toolName, String toolResultId, String reason) {
        return new ToolResult<>(toolResultId, toolName, ToolOutcome.UNAVAILABLE, null,
                "Tool result unavailable: " + reason, Instant.now(), "SYNTHETIC_ADAPTER");
    }
}
