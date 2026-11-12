package com.training.fraudcase.knowledge;

/** Validated policy document metadata from the document front matter. */
public record PolicyDocumentMeta(
        String documentId,
        String title,
        String version,
        String effectiveDate,
        String owner
) {}
