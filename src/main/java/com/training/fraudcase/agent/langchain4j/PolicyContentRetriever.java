package com.training.fraudcase.agent.langchain4j;

import com.training.fraudcase.domain.PolicyPassage;
import com.training.fraudcase.knowledge.PolicyRetriever;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;
import java.util.Map;

/**
 * LangChain4j RetrievalAugmentor source backed by the shared
 * PolicyRetriever. Citation metadata (documentId, version, section,
 * chunkId, score) travels on each TextSegment so prompts and citations
 * stay exact.
 */
public class PolicyContentRetriever implements ContentRetriever {

    private final PolicyRetriever retriever;

    public PolicyContentRetriever(PolicyRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return retriever.retrieve(query.text()).stream()
                .map(this::toContent)
                .toList();
    }

    private Content toContent(PolicyPassage p) {
        Metadata meta = new Metadata()
                .put("documentId", p.documentId())
                .put("version", p.version())
                .put("section", p.section() == null ? "" : p.section())
                .put("chunkId", p.chunkId());
        TextSegment segment = TextSegment.from(
                "[" + p.chunkId() + "] " + p.documentId() + " v" + p.version()
                        + " \"" + p.section() + "\" (score " + p.score() + ")\n" + p.passage(),
                meta);
        return Content.from(segment, Map.of(ContentMetadata.SCORE, p.score()));
    }
}
