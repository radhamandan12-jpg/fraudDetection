package com.training.fraudcase;

import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.knowledge.LexicalPolicyRetriever;
import com.training.fraudcase.knowledge.PolicyIngestionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static com.training.fraudcase.TestFixtures.props;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LexicalPolicyRetrieverTest {

    private LexicalPolicyRetriever retriever;

    @BeforeAll
    void setup() {
        var p = props();
        retriever = new LexicalPolicyRetriever(new PolicyIngestionService(), p);
        retriever.init();
    }

    @Test
    void ingestsAllPolicyChunksWithMetadata() {
        assertTrue(retriever.chunkCount() >= 10, "expected chunks from 4 policy docs");
    }

    @Test
    void unauthorisedQueryReturnsCnpPolicyWithCitationMetadata() {
        List<PolicyPassage> hits = retriever.retrieve(
                "unauthorised card not present transaction new device unusual location authentication customer denies merchant");
        assertFalse(hits.isEmpty());
        assertEquals("POL-CNP-017", hits.get(0).documentId());
        assertTrue(hits.get(0).score() >= 0.35);
        assertTrue(hits.get(0).version() != null && hits.get(0).chunkId() != null);
    }

    @Test
    void duplicateQueryReturnsDupPolicy() {
        List<PolicyPassage> hits = retriever.retrieve(
                "duplicate transaction processing same merchant amount fingerprint");
        assertFalse(hits.isEmpty());
        assertEquals("POL-DUP-009", hits.get(0).documentId());
    }

    @Test
    void abstainsBelowThreshold() {
        List<PolicyPassage> hits = retriever.retrieve("zzzz qqqq xxyy nonsense vocabulary");
        assertTrue(hits.isEmpty(), "expected abstention on unrelated query");
    }

    @Test
    void respectsTopK() {
        List<PolicyPassage> hits = retriever.retrieve("transaction dispute merchant review manual routing");
        assertTrue(hits.size() <= retriever.topK());
    }

    @Test
    void retiredPolicyNeverRetrieved() {
        List<PolicyPassage> hits = retriever.retrieve(
                "legacy manual callback verification outbound call customer before classification");
        assertTrue(hits.stream().noneMatch(p -> p.documentId().equals("POL-LEG-001")),
                "retired document must be excluded from the retrieval corpus");
    }

    @Test
    void duplicateQueryDoesNotMatchRetiredContent() {
        List<PolicyPassage> hits = retriever.retrieve("outbound call verification procedure");
        assertTrue(hits.stream().noneMatch(p -> p.documentId().equals("POL-LEG-001")));
    }
}
