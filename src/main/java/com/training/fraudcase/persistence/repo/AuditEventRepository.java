package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    List<AuditEventEntity> findByCaseIdOrderByTimestampAsc(String caseId);
}
