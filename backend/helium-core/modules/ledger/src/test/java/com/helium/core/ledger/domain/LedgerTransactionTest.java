package com.helium.core.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTransactionTest {
    private final LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:btc", "BTC", BalanceType.EXTERNAL);
    private final LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "BTC", BalanceType.AVAILABLE);

    @Test
    void createsBalancedTransaction() {
        LedgerTransaction transaction = LedgerTransaction.create(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineDraft(external, PostingDirection.DEBIT, new BigDecimal("1.25")),
                new PostingLineDraft(userAvailable, PostingDirection.CREDIT, new BigDecimal("1.25"))
            )
        );

        assertThat(transaction.postingLines()).hasSize(2);
        assertThat(transaction.idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void rejectsUnbalancedTransaction() {
        assertThatThrownBy(() -> LedgerTransaction.create(
            LedgerTransactionType.DEPOSIT,
            "deposit-2",
            "idem-2",
            "bad deposit",
            auditMetadata(),
            List.of(
                new PostingLineDraft(external, PostingDirection.DEBIT, new BigDecimal("1.25")),
                new PostingLineDraft(userAvailable, PostingDirection.CREDIT, new BigDecimal("1.24"))
            )
        )).isInstanceOf(LedgerInvariantViolationException.class)
            .hasMessageContaining("debits must equal credits");
    }

    @Test
    void rejectsNegativePostingAmount() {
        assertThatThrownBy(() -> new PostingLineDraft(external, PostingDirection.DEBIT, new BigDecimal("-1.00")))
            .isInstanceOf(LedgerValidationException.class)
            .hasMessageContaining("positive");
    }

    private static AuditMetadata auditMetadata() {
        return AuditMetadata.of("system", "ledger-test", "corr-1", "cause-1", "test");
    }
}

