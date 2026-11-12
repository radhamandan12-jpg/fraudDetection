package com.training.fraudcase.tools;

import com.training.fraudcase.domain.enums.ToolOutcome;
import com.training.fraudcase.persistence.entity.ToolTraceEntity;
import com.training.fraudcase.persistence.repo.ToolTraceRepository;
import com.training.fraudcase.support.Hashes;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Persists a redacted execution trace for every tool invocation: tool name,
 * argument hash (never raw args), status, duration and a result summary.
 */
@Component
public class ToolTraceRecorder {

    private final ToolTraceRepository repository;

    public ToolTraceRecorder(ToolTraceRepository repository) {
        this.repository = repository;
    }

    public void record(String traceId, String caseId, String toolName, String argsPayload,
                       ToolOutcome outcome, long durationMs, String resultSummary) {
        ToolTraceEntity entity = new ToolTraceEntity();
        entity.setTraceId(traceId);
        entity.setCaseId(caseId == null ? "UNBOUND" : caseId);
        entity.setToolName(toolName);
        entity.setRequestHash(Hashes.sha256(argsPayload == null ? "" : argsPayload));
        entity.setStatus(outcome);
        entity.setDurationMs(durationMs);
        entity.setResultSummary(resultSummary == null ? null
                : resultSummary.substring(0, Math.min(resultSummary.length(), 2000)));
        entity.setInvokedAt(Instant.now());
        repository.save(entity);
    }
}
