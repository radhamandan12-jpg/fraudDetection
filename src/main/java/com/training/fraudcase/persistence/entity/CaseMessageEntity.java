package com.training.fraudcase.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** Case-scoped follow-up conversation turn, persisted for UI history. */
@Entity
@Table(name = "case_messages")
public class CaseMessageEntity {

    @Id
    private String messageId;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
