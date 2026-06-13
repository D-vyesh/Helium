package com.helium.core.authuser.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.helium.core.authuser.application.TokenValue;
import org.junit.jupiter.api.Test;

class Sha256TokenCodecTest {
    private final Sha256TokenCodec tokenCodec = new Sha256TokenCodec();

    @Test
    void generatesRandomTokenAndStoresOnlyDeterministicHash() {
        TokenValue first = tokenCodec.generate();
        TokenValue second = tokenCodec.generate();

        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.tokenHash()).hasSize(64).isEqualTo(tokenCodec.hash(first.rawToken()));
        assertThat(first.tokenHash()).doesNotContain(first.rawToken());
    }
}
