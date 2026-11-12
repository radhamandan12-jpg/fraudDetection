package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.ToolTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolTraceRepository extends JpaRepository<ToolTraceEntity, String> {
    List<ToolTraceEntity> findByCaseIdOrderByInvokedAtAsc(String caseId);
}
