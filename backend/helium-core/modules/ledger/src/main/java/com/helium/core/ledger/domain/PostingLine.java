package com.helium.core.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_posting_lines")
public class PostingLine {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private LedgerAccount account;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 20)
    private PostingDirection direction;

    @Column(name = "amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    protected PostingLine() {
    }

    private PostingLine(LedgerTransaction transaction, LedgerAccount account, PostingDirection direction, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.account = Objects.requireNonNull(account, "account");
        this.assetCode = account.assetCode();
        this.direction = Objects.requireNonNull(direction, "direction");
        this.amount = requirePositiveAmount(amount);
    }

    static PostingLine create(LedgerTransaction transaction, LedgerAccount account, PostingDirection direction, BigDecimal amount) {
        return new PostingLine(transaction, account, direction, amount);
    }

    public UUID id() {
        return id;
    }

    public LedgerAccount account() {
        return account;
    }

    public String assetCode() {
        return assetCode;
    }

    public PostingDirection direction() {
        return direction;
    }

    public BigDecimal amount() {
        return amount;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new LedgerValidationException("posting amount must be positive");
        }
        return amount.stripTrailingZeros();
    }
}
