package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActorType actorType;

    @Column(nullable = false)
    private Instant timestamp;

    /** Hash of the event summary — payloads are never stored raw. */
    @Column(nullable = false)
    private String payloadHash;

    @Column(length = 1024)
    private String summary;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
