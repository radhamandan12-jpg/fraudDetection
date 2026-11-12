package com.training.fraudcase.persistence.repo;

import com.training.fraudcase.persistence.entity.TransactionClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionClaimRepository extends JpaRepository<TransactionClaimEntity, String> {
    Optional<TransactionClaimEntity> findFirstByCaseIdOrderByClaimIdAsc(String caseId);
    List<TransactionClaimEntity> findByTransactionRef(String transactionRef);
}
