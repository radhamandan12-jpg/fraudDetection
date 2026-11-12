package com.training.fraudcase.knowledge;

/** One retrievable chunk. documentId/version/section/chunkId are preserved
 * end-to-end so citations are exact. */
public record PolicyChunk(
        String documentId,
        String title,
        String version,
        String section,
        String chunkId,
        String text
) {}
