package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.RecommendationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, String> {
    Optional<RecommendationEntity> findFirstByCaseIdOrderByCreatedAtDesc(String caseId);
}
