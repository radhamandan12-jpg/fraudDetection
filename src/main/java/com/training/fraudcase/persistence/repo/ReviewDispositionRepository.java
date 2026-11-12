package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.ReviewDispositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewDispositionRepository extends JpaRepository<ReviewDispositionEntity, String> {
    List<ReviewDispositionEntity> findByCaseIdOrderByRecordedAtDesc(String caseId);
}
