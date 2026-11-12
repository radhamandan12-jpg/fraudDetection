package com.training.fraudcase.tools.dto;

import java.time.Instant;

public record CaseSla(
        String queue,
        String priority,
        Instant targetAt,
        String explanation
) {}
