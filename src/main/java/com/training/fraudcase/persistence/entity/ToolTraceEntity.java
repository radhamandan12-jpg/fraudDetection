package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.ToolOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tool_traces")
public class ToolTraceEntity {

    @Id
    private String traceId;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String toolName;

    /** SHA-256 of the argument payload — arguments themselves are not stored. */
    @Column(nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolOutcome status;

    @Column(nullable = false)
    private long durationMs;

    @Lob
    private String resultSummary;

    @Column(nullable = false)
    private Instant invokedAt;

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public ToolOutcome getStatus() { return status; }
    public void setStatus(ToolOutcome status) { this.status = status; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public Instant getInvokedAt() { return invokedAt; }
    public void setInvokedAt(Instant invokedAt) { this.invokedAt = invokedAt; }
}
