package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.CaseMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseMessageRepository extends JpaRepository<CaseMessageEntity, String> {
    List<CaseMessageEntity> findByCaseIdOrderByCreatedAtAsc(String caseId);
}
