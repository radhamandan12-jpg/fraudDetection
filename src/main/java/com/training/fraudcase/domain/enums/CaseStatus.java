package com.training.fraudcase.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Case lifecycle. Transitions are enforced by the application service,
 * never by model output.
 */
public enum CaseStatus {
    NEW,
    ENRICHING,
    INCOMPLETE,
    READY_FOR_REVIEW,
    UNDER_REVIEW,
    ROUTED,
    CLOSED;

    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = Map.of(
            NEW, EnumSet.of(ENRICHING),
            ENRICHING, EnumSet.of(READY_FOR_REVIEW, INCOMPLETE),
            INCOMPLETE, EnumSet.of(ENRICHING, CLOSED),
            READY_FOR_REVIEW, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(ROUTED, CLOSED),
            ROUTED, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(CaseStatus.class)
    );

    public boolean canTransitionTo(CaseStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public Set<CaseStatus> allowedNext() {
        return ALLOWED.getOrDefault(this, Set.of());
    }
}
