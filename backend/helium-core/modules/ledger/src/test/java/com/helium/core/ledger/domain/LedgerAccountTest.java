package com.helium.core.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LedgerAccountTest {
    @Test
    void normalizesAssetCode() {
        LedgerAccount account = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "usdt", BalanceType.AVAILABLE);

        assertThat(account.assetCode()).isEqualTo("USDT");
    }

    @Test
    void rejectsBlankOwnerId() {
        assertThatThrownBy(() -> LedgerAccount.open(LedgerAccountOwnerType.USER, " ", "USDT", BalanceType.AVAILABLE))
            .isInstanceOf(LedgerValidationException.class)
            .hasMessageContaining("ownerId");
    }

    @Test
    void storesNegativeBalancePolicyAtAccountLevel() {
        LedgerAccount account = LedgerAccount.open(
            LedgerAccountOwnerType.SUSPENSE,
            "suspense-1",
            "USDT",
            BalanceType.SUSPENSE,
            true
        );

        assertThat(account.negativeBalanceAllowed()).isTrue();
    }
}
