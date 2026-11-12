package com.training.fraudcase.knowledge;

import com.training.fraudcase.config.CapstoneProperties;
import com.training.fraudcase.domain.PolicyPassage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic in-memory retriever: cosine similarity over token-frequency
 * vectors with a small stopword list and a boost for section/title hits.
 * Works in every processor mode (including rules) with no embedding model.
 * Swap point for a Spring AI VectorStore or LangChain4j EmbeddingStore
 * adapter behind the same interface.
 */
@Component
public class LexicalPolicyRetriever implements PolicyRetriever {

    private static final Logger log = LoggerFactory.getLogger(LexicalPolicyRetriever.class);

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "for", "on", "is", "are",
            "was", "be", "by", "with", "that", "this", "it", "as", "at", "from",
            "customer", "case", "transaction", "dispute");

    private final PolicyIngestionService ingestion;
    private final int topK;
    private final double threshold;

    private List<PolicyChunk> chunks = List.of();
    private Map<String, Map<String, Integer>> chunkVectors = Map.of();

    public LexicalPolicyRetriever(PolicyIngestionService ingestion, CapstoneProperties props) {
        this.ingestion = ingestion;
        this.topK = props.getRetrieval().getTopK();
        this.threshold = props.getRetrieval().getSimilarityThreshold();
    }

    @PostConstruct
    public void init() {
        this.chunks = ingestion.ingestAll();
        Map<String, Map<String, Integer>> vectors = new HashMap<>();
        for (PolicyChunk c : chunks) {
            vectors.put(c.chunkId(), tokenize(c.title() + " " + c.section() + " " + c.text()));
        }
        this.chunkVectors = vectors;
    }

    @Override
    public List<PolicyPassage> retrieve(String query) {
        Map<String, Integer> qv = tokenize(query);
        List<PolicyPassage> scored = new ArrayList<>();
        for (PolicyChunk c : chunks) {
            double score = cosine(qv, chunkVectors.get(c.chunkId()));
            if (score >= threshold) {
                scored.add(new PolicyPassage(c.documentId(), c.version(), c.section(),
                        c.chunkId(), Math.round(score * 1000.0) / 1000.0, c.text()));
            }
        }
        scored.sort(Comparator.comparingDouble(PolicyPassage::score).reversed());
        List<PolicyPassage> top = scored.stream().limit(topK).toList();
        if (top.isEmpty()) {
            log.info("Policy retrieval abstained: no chunk met threshold {} for query '{}'",
                    threshold, abbrev(query));
        }
        return top;
    }

    @Override
    public double similarityThreshold() {
        return threshold;
    }

    @Override
    public int topK() {
        return topK;
    }

    public int chunkCount() {
        return chunks.size();
    }

    private Map<String, Integer> tokenize(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() > 2 && !STOPWORDS.contains(raw)) {
                tf.merge(raw, 1, Integer::sum);
            }
        }
        return tf;
    }

    private double cosine(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (var e : a.entrySet()) {
            na += e.getValue() * e.getValue();
            Integer bv = b.get(e.getKey());
            if (bv != null) {
                dot += e.getValue() * bv;
            }
        }
        for (int v : b.values()) {
            nb += v * v;
        }
        return na == 0 || nb == 0 ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String abbrev(String q) {
        return q.length() > 80 ? q.substring(0, 80) + "…" : q;
    }
}
