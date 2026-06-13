package com.helium.core.authuser.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Argon2idPasswordHasherTest {
    private final Argon2idPasswordHasher passwordHasher = new Argon2idPasswordHasher();

    @Test
    void hashesAndVerifiesPasswordUsingArgon2id() {
        String hash = passwordHasher.hash("A-strong-password-123");

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).doesNotContain("A-strong-password-123");
        assertThat(passwordHasher.matches("A-strong-password-123", hash)).isTrue();
        assertThat(passwordHasher.matches("wrong-password", hash)).isFalse();
    }
}
