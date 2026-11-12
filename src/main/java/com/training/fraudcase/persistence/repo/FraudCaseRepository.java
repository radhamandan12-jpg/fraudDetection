package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.FraudCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudCaseRepository extends JpaRepository<FraudCaseEntity, String> {
}
