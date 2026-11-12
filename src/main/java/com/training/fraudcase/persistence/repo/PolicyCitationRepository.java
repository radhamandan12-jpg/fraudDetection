package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.PolicyCitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyCitationRepository extends JpaRepository<PolicyCitationEntity, String> {
    List<PolicyCitationEntity> findByCaseIdOrderByScoreDesc(String caseId);
    List<PolicyCitationEntity> findByRecommendationId(String recommendationId);
}
