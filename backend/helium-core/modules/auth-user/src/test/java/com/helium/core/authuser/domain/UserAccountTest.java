package com.helium.core.authuser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountTest {
    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");

    @Test
    void verifiesEmailAndActivatesPendingAccount() {
        UserAccount account = UserAccount.register(" User@Example.com ", "User", NOW);

        account.verifyEmail(NOW.plusSeconds(1));

        assertThat(account.email()).isEqualTo("user@example.com");
        assertThat(account.status()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(account.emailVerifiedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void locksAfterConfiguredFailedAttemptsAndAutomaticallyUnlocksAfterDeadline() {
        UserAccount account = activeAccount();

        assertThat(account.recordFailedLogin(2, Duration.ofMinutes(15), NOW.plusSeconds(1))).isFalse();
        assertThat(account.recordFailedLogin(2, Duration.ofMinutes(15), NOW.plusSeconds(2))).isTrue();
        assertThat(account.status()).isEqualTo(UserAccountStatus.LOCKED);
        assertThat(account.canAuthenticate(NOW.plus(Duration.ofMinutes(16)))).isTrue();
        assertThat(account.status()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(account.failedLoginAttempts()).isZero();
    }

    @Test
    void suspendedAccountCannotAuthenticate() {
        UserAccount account = activeAccount();

        account.suspend(NOW.plusSeconds(1));

        assertThat(account.canAuthenticate(NOW.plusSeconds(2))).isFalse();
        assertThatThrownBy(() -> account.unlock(NOW.plusSeconds(3)))
            .isInstanceOf(AuthValidationException.class);
    }

    private static UserAccount activeAccount() {
        UserAccount account = UserAccount.register("user@example.com", "User", NOW);
        account.verifyEmail(NOW);
        return account;
    }
}
