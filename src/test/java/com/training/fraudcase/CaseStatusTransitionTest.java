package com.training.fraudcase;

import com.training.fraudcase.domain.enums.CaseStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseStatusTransitionTest {

    @Test
    void allowedTransitionsFollowSpecTable() {
        assertTrue(CaseStatus.NEW.canTransitionTo(CaseStatus.ENRICHING));
        assertTrue(CaseStatus.ENRICHING.canTransitionTo(CaseStatus.READY_FOR_REVIEW));
        assertTrue(CaseStatus.ENRICHING.canTransitionTo(CaseStatus.INCOMPLETE));
        assertTrue(CaseStatus.INCOMPLETE.canTransitionTo(CaseStatus.ENRICHING));
        assertTrue(CaseStatus.INCOMPLETE.canTransitionTo(CaseStatus.CLOSED));
        assertTrue(CaseStatus.READY_FOR_REVIEW.canTransitionTo(CaseStatus.UNDER_REVIEW));
        assertTrue(CaseStatus.UNDER_REVIEW.canTransitionTo(CaseStatus.ROUTED));
        assertTrue(CaseStatus.UNDER_REVIEW.canTransitionTo(CaseStatus.CLOSED));
        assertTrue(CaseStatus.ROUTED.canTransitionTo(CaseStatus.CLOSED));
    }

    @Test
    void illegalTransitionsRejected() {
        assertFalse(CaseStatus.NEW.canTransitionTo(CaseStatus.CLOSED));
        assertFalse(CaseStatus.NEW.canTransitionTo(CaseStatus.READY_FOR_REVIEW));
        assertFalse(CaseStatus.UNDER_REVIEW.canTransitionTo(CaseStatus.ENRICHING));
        assertFalse(CaseStatus.READY_FOR_REVIEW.canTransitionTo(CaseStatus.ROUTED));
        assertFalse(CaseStatus.CLOSED.canTransitionTo(CaseStatus.ENRICHING));
        assertFalse(CaseStatus.ROUTED.canTransitionTo(CaseStatus.UNDER_REVIEW));
    }
}
