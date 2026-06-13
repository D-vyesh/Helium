package com.helium.core.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "ledger_balance_snapshots",
    uniqueConstraints = @UniqueConstraint(name = "uk_ledger_balance_snapshots_account", columnNames = "account_id")
)
public class BalanceSnapshot {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private LedgerAccount account;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Column(name = "current_balance", nullable = false, precision = 38, scale = 18)
    private BigDecimal currentBalance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BalanceSnapshot() {
    }

    private BalanceSnapshot(LedgerAccount account) {
        this.id = UUID.randomUUID();
        this.account = Objects.requireNonNull(account, "account");
        this.assetCode = account.assetCode();
        this.currentBalance = BigDecimal.ZERO;
        this.updatedAt = Instant.now();
    }

    public static BalanceSnapshot zeroFor(LedgerAccount account) {
        return new BalanceSnapshot(account);
    }

    public UUID id() {
        return id;
    }

    public LedgerAccount account() {
        return account;
    }

    public BigDecimal currentBalance() {
        return currentBalance;
    }

    public void apply(PostingLine postingLine) {
        if (!account.id().equals(postingLine.account().id())) {
            throw new LedgerInvariantViolationException("posting line account does not match balance snapshot account");
        }

        BigDecimal nextBalance = postingLine.direction() == account.normalBalanceDirection()
            ? currentBalance.add(postingLine.amount())
            : currentBalance.subtract(postingLine.amount());

        if (nextBalance.signum() < 0 && !account.negativeBalanceAllowed()) {
            throw new LedgerInvariantViolationException("posting would create a negative balance for account " + account.id());
        }

        this.currentBalance = nextBalance.stripTrailingZeros();
        this.updatedAt = Instant.now();
    }
}

