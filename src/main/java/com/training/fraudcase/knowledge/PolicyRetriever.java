package com.training.fraudcase.knowledge;

import com.training.fraudcase.domain.PolicyPassage;

import java.util.List;

/**
 * Framework-neutral policy retrieval. Returns the top-k passages whose
 * score meets the configured threshold; an empty list is a valid answer —
 * callers must abstain rather than invent procedure.
 */
public interface PolicyRetriever {

    List<PolicyPassage> retrieve(String query);

    double similarityThreshold();

    int topK();
}
