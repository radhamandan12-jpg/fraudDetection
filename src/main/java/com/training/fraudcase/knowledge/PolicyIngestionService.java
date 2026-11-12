package com.training.fraudcase.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads policy documents from classpath:policies/. Each document uses
 * simple front matter:
 * <pre>
 * ---
 * documentId: POL-CNP-017
 * title: Card-not-present unauthorised transactions
 * version: 3.2
 * effectiveDate: 2026-01-15
 * owner: Fraud Policy Office
 * ---
 * ## 3.1 Scope
 * ...
 * </pre>
 * Documents are validated (all metadata required) then split on '## '
 * headings so a rule is never broken across unrelated chunks.
 */
@Service
public class PolicyIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionService.class);

    public List<PolicyChunk> ingestAll() {
        List<PolicyChunk> all = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:policies/*.md");
            for (Resource resource : resources) {
                all.addAll(ingest(resource));
            }
        } catch (IOException e) {
            throw new PolicyIngestionException("Failed to scan policy resources: " + e.getMessage());
        }
        log.info("Ingested {} policy chunks", all.size());
        return all;
    }

    List<PolicyChunk> ingest(Resource resource) {
        String body;
        try {
            body = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PolicyIngestionException("Cannot read " + resource.getFilename());
        }
        PolicyDocumentMeta meta = parseFrontMatter(body, resource.getFilename());
        return split(meta, stripFrontMatter(body));
    }

    private PolicyDocumentMeta parseFrontMatter(String body, String name) {
        if (!body.startsWith("---")) {
            throw new PolicyIngestionException("Missing front matter in " + name);
        }
        int end = body.indexOf("\n---", 3);
        if (end < 0) {
            throw new PolicyIngestionException("Unterminated front matter in " + name);
        }
        Map<String, String> fields = new HashMap<>();
        for (String line : body.substring(3, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String value = line.substring(colon + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                fields.put(line.substring(0, colon).trim(), value);
            }
        }
        PolicyDocumentMeta meta = new PolicyDocumentMeta(
                fields.get("documentId"), fields.get("title"), fields.get("version"),
                fields.get("effectiveDate"), fields.get("owner"));
        if (meta.documentId() == null || meta.title() == null || meta.version() == null
                || meta.effectiveDate() == null || meta.owner() == null) {
            throw new PolicyIngestionException(
                    "Policy " + name + " missing required metadata (documentId/title/version/effectiveDate/owner)");
        }
        return meta;
    }

    private String stripFrontMatter(String body) {
        int end = body.indexOf("\n---", 3);
        return body.substring(end + 4).trim();
    }

    /** Split on '## ' headings. A heading line yields one chunk containing
     *  the section text up to the next heading. */
    private List<PolicyChunk> split(PolicyDocumentMeta meta, String content) {
        List<PolicyChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        String section = "preamble";
        StringBuilder current = new StringBuilder();
        int index = 0;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                index += flush(chunks, meta, section, current, index);
                section = line.substring(3).trim();
                current = new StringBuilder();
            } else {
                current.append(line).append('\n');
            }
        }
        flush(chunks, meta, section, current, index);
        return chunks;
    }

    private int flush(List<PolicyChunk> chunks, PolicyDocumentMeta meta,
                      String section, StringBuilder current, int index) {
        String text = current.toString().trim();
        if (text.isEmpty()) {
            return 0;
        }
        String chunkId = meta.documentId().toLowerCase() + "-c" + String.format("%03d", index + 1);
        chunks.add(new PolicyChunk(meta.documentId(), meta.title(), meta.version(),
                section, chunkId, text));
        return 1;
    }
}
