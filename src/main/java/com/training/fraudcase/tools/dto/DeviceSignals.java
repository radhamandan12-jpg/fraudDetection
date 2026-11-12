package com.training.fraudcase.tools.dto;

import com.training.fraudcase.domain.enums.TrustLevel;

import java.time.Instant;
import java.util.List;

public record DeviceSignals(
        boolean deviceKnown,
        Instant firstSeenAt,
        TrustLevel trustLevel,
        List<String> observations
) {}
