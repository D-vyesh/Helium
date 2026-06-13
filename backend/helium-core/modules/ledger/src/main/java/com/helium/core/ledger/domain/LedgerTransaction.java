package com.helium.core.ledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(
    name = "ledger_transactions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ledger_transactions_idempotency_key",
        columnNames = "idempotency_key"
    )
)
public class LedgerTransaction {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 60)
    private LedgerTransactionType type;

    @Column(name = "business_reference", nullable = false, length = 160)
    private String businessReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Embedded
    private AuditMetadata auditMetadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<PostingLine> postingLines = new ArrayList<>();

    protected LedgerTransaction() {
    }

    private LedgerTransaction(
        LedgerTransactionType type,
        String businessReference,
        String idempotencyKey,
        String description,
        AuditMetadata auditMetadata
    ) {
        this.id = UUID.randomUUID();
        this.type = Objects.requireNonNull(type, "type");
        this.businessReference = requireText(businessReference, "businessReference");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.description = requireText(description, "description");
        this.auditMetadata = Objects.requireNonNull(auditMetadata, "auditMetadata");
        this.createdAt = Instant.now();
    }

    public static LedgerTransaction create(
        LedgerTransactionType type,
        String businessReference,
        String idempotencyKey,
        String description,
        AuditMetadata auditMetadata,
        List<PostingLineDraft> drafts
    ) {
        validateDrafts(drafts);
        LedgerTransaction transaction = new LedgerTransaction(type, businessReference, idempotencyKey, description, auditMetadata);
        drafts.forEach(draft -> transaction.postingLines.add(PostingLine.create(transaction, draft.account(), draft.direction(), draft.amount())));
        return transaction;
    }

    public UUID id() {
        return id;
    }

    public LedgerTransactionType type() {
        return type;
    }

    public String businessReference() {
        return businessReference;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public List<PostingLine> postingLines() {
        return Collections.unmodifiableList(postingLines);
    }

    private static void validateDrafts(List<PostingLineDraft> drafts) {
        if (drafts == null || drafts.size() < 2) {
            throw new LedgerInvariantViolationException("ledger transaction requires at least two posting lines");
        }

        Map<String, BigDecimal> debits = new LinkedHashMap<>();
        Map<String, BigDecimal> credits = new LinkedHashMap<>();

        for (PostingLineDraft draft : drafts) {
            String assetCode = draft.account().assetCode();
            if (draft.direction() == PostingDirection.DEBIT) {
                debits.merge(assetCode, draft.amount(), BigDecimal::add);
            } else {
                credits.merge(assetCode, draft.amount(), BigDecimal::add);
            }
        }

        if (!debits.keySet().equals(credits.keySet())) {
            throw new LedgerInvariantViolationException("debit and credit asset sets must match");
        }

        for (String assetCode : debits.keySet()) {
            if (debits.get(assetCode).compareTo(credits.get(assetCode)) != 0) {
                throw new LedgerInvariantViolationException("debits must equal credits for asset " + assetCode);
            }
        }
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new LedgerValidationException(field + " is required");
        }
        return value;
    }
}
