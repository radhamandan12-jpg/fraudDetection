package com.training.fraudcase.domain;

import com.training.fraudcase.domain.enums.ToolOutcome;

import java.time.Instant;

/** Redacted tool execution trace — no raw sensitive payloads. */
public record ToolTraceView(
        String traceId,
        String toolName,
        String requestHash,
        ToolOutcome status,
        long durationMs,
        String resultSummary,
        Instant invokedAt
) {}
