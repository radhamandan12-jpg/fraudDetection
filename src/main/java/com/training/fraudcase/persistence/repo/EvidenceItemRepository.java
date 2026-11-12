package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.EvidenceItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceItemRepository extends JpaRepository<EvidenceItemEntity, String> {
    List<EvidenceItemEntity> findByCaseIdOrderByObservedAtAsc(String caseId);
    void deleteByCaseId(String caseId);
}
