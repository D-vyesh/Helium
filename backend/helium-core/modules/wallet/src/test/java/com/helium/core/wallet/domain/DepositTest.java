package com.helium.core.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepositTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void confirmsWhenRequiredConfirmationsAreReached() {
        Deposit deposit = Deposit.detect(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "btc",
            "btc-mainnet",
            "tx-1",
            0,
            new BigDecimal("1.25"),
            3,
            NOW
        );

        deposit.updateConfirmations(3, NOW.plusSeconds(60));

        assertThat(deposit.status()).isEqualTo(DepositStatus.CONFIRMED);
    }

    @Test
    void returnsToPendingWhenConfirmationsDecreaseBeforePosting() {
        Deposit deposit = Deposit.detect(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC",
            "BTC",
            "tx-1",
            0,
            BigDecimal.ONE,
            3,
            NOW
        );
        deposit.updateConfirmations(2, NOW.plusSeconds(30));
        deposit.updateConfirmations(1, NOW.plusSeconds(60));

        assertThat(deposit.status()).isEqualTo(DepositStatus.PENDING_CONFIRMATIONS);
    }

    @Test
    void alwaysStartsDetectedWithZeroConfirmations() {
        Deposit deposit = Deposit.detect(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC",
            "BTC",
            "tx-1",
            0,
            BigDecimal.ONE,
            1,
            NOW
        );

        assertThat(deposit.status()).isEqualTo(DepositStatus.DETECTED);
    }

    @Test
    void preservesTheLedgerPostingReferenceWhenMarkedReorged() {
        Deposit deposit = Deposit.detect(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC",
            "BTC",
            "tx-1",
            0,
            BigDecimal.ONE,
            1,
            NOW
        );
        UUID ledgerTransactionId = UUID.randomUUID();
        deposit.updateConfirmations(1, NOW.plusSeconds(30));
        deposit.markPosted(ledgerTransactionId, NOW.plusSeconds(31));

        deposit.markReorged(NOW.plusSeconds(60), "REORG_DETECTED");

        assertThat(deposit.status()).isEqualTo(DepositStatus.REORGED);
        assertThat(deposit.ledgerTransactionId()).isEqualTo(ledgerTransactionId);
    }
}
