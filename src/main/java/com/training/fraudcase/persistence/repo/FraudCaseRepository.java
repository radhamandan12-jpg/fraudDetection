package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.FraudCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FraudCaseRepository extends JpaRepository<FraudCaseEntity, String> {
    Optional<FraudCaseEntity> findTopByOrderByCaseIdDesc();
}
