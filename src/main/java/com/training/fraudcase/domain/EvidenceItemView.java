package com.training.fraudcase.domain;

import com.training.fraudcase.domain.enums.EvidenceType;
import com.training.fraudcase.domain.enums.Provenance;
import com.training.fraudcase.domain.enums.Severity;

import java.time.Instant;

/** One material fact or claim, with provenance. */
public record EvidenceItemView(
        String evidenceId,
        EvidenceType type,
        Severity severity,
        String valueSummary,
        Provenance provenance,
        Instant observedAt,
        String sourceTool
) {}
