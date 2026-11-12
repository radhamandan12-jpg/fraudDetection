package com.training.fraudcase.tools.dto;

import java.time.Instant;

public record AuditEventResult(
        String eventId,
        String eventType,
        Instant recordedAt
) {}
