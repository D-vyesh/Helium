package com.helium.core.authuser.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserSessionTest {
    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");

    @Test
    void expiresSessionAtDeadline() {
        UserSession session = UserSession.create(
            UUID.randomUUID(),
            "0".repeat(64),
            "127.0.0.1",
            "test",
            Duration.ofMinutes(10),
            NOW
        );

        assertThat(session.isActive(NOW.plus(Duration.ofMinutes(10)))).isFalse();
        assertThat(session.status()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void revokesActiveSession() {
        UserSession session = UserSession.create(
            UUID.randomUUID(),
            "0".repeat(64),
            "127.0.0.1",
            "test",
            Duration.ofMinutes(10),
            NOW
        );

        session.revoke("password changed", NOW.plusSeconds(1));

        assertThat(session.status()).isEqualTo(SessionStatus.REVOKED);
        assertThat(session.isActive(NOW.plusSeconds(2))).isFalse();
    }
}
