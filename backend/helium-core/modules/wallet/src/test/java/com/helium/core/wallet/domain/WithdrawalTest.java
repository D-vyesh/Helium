package com.helium.core.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WithdrawalTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void followsManualApprovalBroadcastConfirmationLifecycle() {
        Withdrawal withdrawal = withdrawal();

        withdrawal.approve("ops-1", NOW.plusSeconds(1));
        withdrawal.recordBroadcast("tx-out-1", NOW.plusSeconds(2));
        withdrawal.confirm(UUID.randomUUID(), NOW.plusSeconds(3));

        assertThat(withdrawal.status()).isEqualTo(WithdrawalStatus.CONFIRMED);
        assertThat(withdrawal.broadcastTxHash()).isEqualTo("tx-out-1");
        assertThat(withdrawal.totalDebit()).isEqualByComparingTo("1.01");
    }

    @Test
    void rejectsBroadcastBeforeApproval() {
        Withdrawal withdrawal = withdrawal();

        assertThatThrownBy(() -> withdrawal.recordBroadcast("tx-out-1", NOW.plusSeconds(1)))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("APPROVED");
    }

    private static Withdrawal withdrawal() {
        return Withdrawal.request(
            "client-1",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            UUID.randomUUID(),
            "USDT",
            "TRON",
            "T-address",
            null,
            BigDecimal.ONE,
            new BigDecimal("0.01"),
            UUID.randomUUID(),
            NOW
        );
    }
}
