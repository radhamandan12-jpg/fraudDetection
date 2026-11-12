package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.Disposition;
import com.training.fraudcase.domain.enums.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "review_dispositions")
public class ReviewDispositionEntity {

    @Id
    private String reviewId;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)", nullable = false)
    private Disposition disposition;

    /** Mandatory reviewer reason. */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** Route chosen when disposition is ACCEPTED or REROUTED. */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private Route selectedRoute;

    @Column(nullable = false)
    private Instant recordedAt;

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public Disposition getDisposition() { return disposition; }
    public void setDisposition(Disposition disposition) { this.disposition = disposition; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Route getSelectedRoute() { return selectedRoute; }
    public void setSelectedRoute(Route selectedRoute) { this.selectedRoute = selectedRoute; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
