package com.helium.core.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceSnapshotTest {
    @Test
    void creditIncreasesUserLiabilityBalanceAndDebitReducesIt() {
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "USDT", BalanceType.AVAILABLE);
        LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:usdt", "USDT", BalanceType.EXTERNAL);

        BalanceSnapshot snapshot = BalanceSnapshot.zeroFor(userAvailable);
        LedgerTransaction deposit = LedgerTransaction.create(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-deposit-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineDraft(external, PostingDirection.DEBIT, new BigDecimal("100.00")),
                new PostingLineDraft(userAvailable, PostingDirection.CREDIT, new BigDecimal("100.00"))
            )
        );

        snapshot.apply(lineForAccount(deposit, userAvailable));
        assertThat(snapshot.currentBalance()).isEqualByComparingTo("100.00");

        LedgerAccount userLocked = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "USDT", BalanceType.LOCKED);
        LedgerTransaction reservation = LedgerTransaction.create(
            LedgerTransactionType.GENERAL,
            "order-1",
            "idem-order-1",
            "reserve funds",
            auditMetadata(),
            List.of(
                new PostingLineDraft(userAvailable, PostingDirection.DEBIT, new BigDecimal("25.00")),
                new PostingLineDraft(userLocked, PostingDirection.CREDIT, new BigDecimal("25.00"))
            )
        );

        snapshot.apply(lineForAccount(reservation, userAvailable));
        assertThat(snapshot.currentBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void rejectsNegativeUserBalance() {
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "USDT", BalanceType.AVAILABLE);
        LedgerAccount userLocked = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "USDT", BalanceType.LOCKED);
        BalanceSnapshot snapshot = BalanceSnapshot.zeroFor(userAvailable);

        LedgerTransaction reservation = LedgerTransaction.create(
            LedgerTransactionType.GENERAL,
            "order-2",
            "idem-order-2",
            "reserve too much",
            auditMetadata(),
            List.of(
                new PostingLineDraft(userAvailable, PostingDirection.DEBIT, new BigDecimal("1.00")),
                new PostingLineDraft(userLocked, PostingDirection.CREDIT, new BigDecimal("1.00"))
            )
        );

        assertThatThrownBy(() -> snapshot.apply(lineForAccount(reservation, userAvailable)))
            .isInstanceOf(LedgerInvariantViolationException.class)
            .hasMessageContaining("negative balance");
    }

    private static PostingLine lineForAccount(LedgerTransaction transaction, LedgerAccount account) {
        return transaction.postingLines().stream()
            .filter(line -> line.account().id().equals(account.id()))
            .findFirst()
            .orElseThrow();
    }

    private static AuditMetadata auditMetadata() {
        return AuditMetadata.of("system", "ledger-test", "corr-1", "cause-1", "test");
    }
}

