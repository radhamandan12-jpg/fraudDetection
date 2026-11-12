package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.CaseChannel;
import com.training.fraudcase.domain.enums.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "fraud_cases")
public class FraudCaseEntity {

    @Id
    private String caseId;

    @Column(nullable = false)
    private String maskedCustomerRef;

    private String homeRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    private CaseChannel caseChannel;

    @Column(nullable = false)
    private Instant reportedAt;

    @Column(nullable = false)
    private String analystId;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getMaskedCustomerRef() { return maskedCustomerRef; }
    public void setMaskedCustomerRef(String maskedCustomerRef) { this.maskedCustomerRef = maskedCustomerRef; }
    public String getHomeRegion() { return homeRegion; }
    public void setHomeRegion(String homeRegion) { this.homeRegion = homeRegion; }
    public CaseStatus getStatus() { return status; }
    public void setStatus(CaseStatus status) { this.status = status; }
    public CaseChannel getCaseChannel() { return caseChannel; }
    public void setCaseChannel(CaseChannel caseChannel) { this.caseChannel = caseChannel; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }
    public String getAnalystId() { return analystId; }
    public void setAnalystId(String analystId) { this.analystId = analystId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
