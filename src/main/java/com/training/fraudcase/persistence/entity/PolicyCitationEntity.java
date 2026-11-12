package com.training.fraudcase.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "policy_citations")
public class PolicyCitationEntity {

    @Id
    private String citationId;

    @Column(nullable = false)
    private String recommendationId;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String documentId;

    @Column(nullable = false)
    private String version;

    private String section;

    @Column(nullable = false)
    private String chunkId;

    @Column(nullable = false)
    private double score;

    public String getCitationId() { return citationId; }
    public void setCitationId(String citationId) { this.citationId = citationId; }
    public String getRecommendationId() { return recommendationId; }
    public void setRecommendationId(String recommendationId) { this.recommendationId = recommendationId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
