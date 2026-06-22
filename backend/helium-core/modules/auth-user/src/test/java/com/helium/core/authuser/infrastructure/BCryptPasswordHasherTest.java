package com.helium.core.authuser.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {
    private final BCryptPasswordHasher passwordHasher = new BCryptPasswordHasher();

    @Test
    void hashesAndVerifiesPasswordUsingBCrypt() {
        String hash = passwordHasher.hash("A-strong-password-123");

        assertThat(hash).startsWith("$2");
        assertThat(hash).doesNotContain("A-strong-password-123");
        assertThat(passwordHasher.matches("A-strong-password-123", hash)).isTrue();
        assertThat(passwordHasher.matches("wrong-password", hash)).isFalse();
    }
}
