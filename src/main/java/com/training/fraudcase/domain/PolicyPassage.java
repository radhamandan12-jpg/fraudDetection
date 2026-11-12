package com.training.fraudcase.domain;

/** A retrieved policy chunk with full citation metadata. */
public record PolicyPassage(
        String documentId,
        String version,
        String section,
        String chunkId,
        double score,
        String passage
) {}
