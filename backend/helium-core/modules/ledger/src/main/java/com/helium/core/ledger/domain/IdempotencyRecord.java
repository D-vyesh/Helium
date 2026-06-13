package com.helium.core.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private IdempotencyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String idempotencyKey, String requestHash, LedgerTransaction transaction) {
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.requestHash = requireText(requestHash, "requestHash");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.status = IdempotencyStatus.POSTED;
        this.createdAt = Instant.now();
    }

    public static IdempotencyRecord posted(String idempotencyKey, String requestHash, LedgerTransaction transaction) {
        return new IdempotencyRecord(idempotencyKey, requestHash, transaction);
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public UUID transactionId() {
        return transaction.id();
    }

    public String requestHash() {
        return requestHash;
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new LedgerValidationException(field + " is required");
        }
        return value;
    }
}
