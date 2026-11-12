package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.EvidenceType;
import com.training.fraudcase.domain.enums.Provenance;
import com.training.fraudcase.domain.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evidence_items")
public class EvidenceItemEntity {

    @Id
    private String evidenceId;

    @Column(nullable = false)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Lob
    @Column(nullable = false)
    private String valueSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provenance provenance;

    @Column(nullable = false)
    private Instant observedAt;

    private String sourceTool;

    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public EvidenceType getType() { return type; }
    public void setType(EvidenceType type) { this.type = type; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getValueSummary() { return valueSummary; }
    public void setValueSummary(String valueSummary) { this.valueSummary = valueSummary; }
    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public String getSourceTool() { return sourceTool; }
    public void setSourceTool(String sourceTool) { this.sourceTool = sourceTool; }
}
