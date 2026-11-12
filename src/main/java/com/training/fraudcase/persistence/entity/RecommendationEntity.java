package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.Classification;
import com.training.fraudcase.domain.enums.RiskLevel;
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
@Table(name = "recommendations")
public class RecommendationEntity {

    @Id
    private String recommendationId;

    @Column(nullable = false)
    private String caseId;

    /** rules | spring-ai | langchain4j */
    @Column(nullable = false)
    private String mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Classification classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Route route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private double confidence;

    @Lob
    @Column(nullable = false)
    private String rationale;

    @Lob
    private String evidenceIdsJson;

    @Lob
    private String missingInformationJson;

    @Lob
    private String ruleIdsJson;

    @Lob
    private String toolTraceIdsJson;

    @Lob
    private String appliedRulesJson;

    @Column(nullable = false)
    private boolean humanReviewRequired;

    @Column(nullable = false)
    private Instant createdAt;

    public String getRecommendationId() { return recommendationId; }
    public void setRecommendationId(String recommendationId) { this.recommendationId = recommendationId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) { this.classification = classification; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getEvidenceIdsJson() { return evidenceIdsJson; }
    public void setEvidenceIdsJson(String evidenceIdsJson) { this.evidenceIdsJson = evidenceIdsJson; }
    public String getMissingInformationJson() { return missingInformationJson; }
    public void setMissingInformationJson(String missingInformationJson) { this.missingInformationJson = missingInformationJson; }
    public String getRuleIdsJson() { return ruleIdsJson; }
    public void setRuleIdsJson(String ruleIdsJson) { this.ruleIdsJson = ruleIdsJson; }
    public String getToolTraceIdsJson() { return toolTraceIdsJson; }
    public void setToolTraceIdsJson(String toolTraceIdsJson) { this.toolTraceIdsJson = toolTraceIdsJson; }
    public String getAppliedRulesJson() { return appliedRulesJson; }
    public void setAppliedRulesJson(String appliedRulesJson) { this.appliedRulesJson = appliedRulesJson; }
    public boolean isHumanReviewRequired() { return humanReviewRequired; }
    public void setHumanReviewRequired(boolean humanReviewRequired) { this.humanReviewRequired = humanReviewRequired; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
