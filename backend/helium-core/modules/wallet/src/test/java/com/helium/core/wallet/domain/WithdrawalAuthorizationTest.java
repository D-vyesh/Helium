package com.helium.core.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WithdrawalAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TOKEN_HASH = "a".repeat(64);

    @Test
    void requiresBothEmailAndMfaBeforeAuthorizationCompletes() {
        WithdrawalAuthorization authorization = WithdrawalAuthorization.issue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TOKEN_HASH,
            NOW.plusSeconds(900),
            NOW
        );

        authorization.confirmEmail(TOKEN_HASH, NOW.plusSeconds(1));
        assertThat(authorization.isConfirmed()).isFalse();

        authorization.confirmMfa(NOW.plusSeconds(2));
        assertThat(authorization.isConfirmed()).isTrue();
    }

    @Test
    void rejectsAnInvalidOrExpiredEmailToken() {
        WithdrawalAuthorization authorization = WithdrawalAuthorization.issue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TOKEN_HASH,
            NOW.plusSeconds(60),
            NOW
        );

        assertThatThrownBy(() -> authorization.confirmEmail("b".repeat(64), NOW.plusSeconds(1)))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("invalid");
        assertThatThrownBy(() -> authorization.confirmEmail(TOKEN_HASH, NOW.plusSeconds(61)))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("expired");
    }
}
