package com.training.fraudcase.persistence.entity;

import com.training.fraudcase.domain.enums.CardPossession;
import com.training.fraudcase.domain.enums.IssueType;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_claims")
public class TransactionClaimEntity {

    @Id
    private String claimId;

    @Column(nullable = false)
    private String caseId;

    @Column(nullable = false)
    private String transactionRef;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private TransactionStatus transactionStatus;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private TransactionChannel transactionChannel;

    private String maskedMerchant;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)", nullable = false)
    private IssueType issueType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private CardPossession cardPossession;

    private Boolean merchantRecognised;

    @Column(nullable = false)
    private boolean syntheticDataConfirmed;

    @Column(nullable = false)
    private boolean reviewRequired;

    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public TransactionStatus getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(TransactionStatus transactionStatus) { this.transactionStatus = transactionStatus; }
    public TransactionChannel getTransactionChannel() { return transactionChannel; }
    public void setTransactionChannel(TransactionChannel transactionChannel) { this.transactionChannel = transactionChannel; }
    public String getMaskedMerchant() { return maskedMerchant; }
    public void setMaskedMerchant(String maskedMerchant) { this.maskedMerchant = maskedMerchant; }
    public IssueType getIssueType() { return issueType; }
    public void setIssueType(IssueType issueType) { this.issueType = issueType; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public CardPossession getCardPossession() { return cardPossession; }
    public void setCardPossession(CardPossession cardPossession) { this.cardPossession = cardPossession; }
    public Boolean getMerchantRecognised() { return merchantRecognised; }
    public void setMerchantRecognised(Boolean merchantRecognised) { this.merchantRecognised = merchantRecognised; }
    public boolean isSyntheticDataConfirmed() { return syntheticDataConfirmed; }
    public void setSyntheticDataConfirmed(boolean syntheticDataConfirmed) { this.syntheticDataConfirmed = syntheticDataConfirmed; }
    public boolean isReviewRequired() { return reviewRequired; }
    public void setReviewRequired(boolean reviewRequired) { this.reviewRequired = reviewRequired; }
}
