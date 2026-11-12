package com.training.fraudcase.agent.common;

/** Citation reference inside the recommendation schema. */
public record PolicyCitationRef(
        String documentId,
        String version,
        String section,
        String chunkId,
        Double score
) {}
