package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.application.TokenCodec;
import com.helium.core.authuser.application.TokenValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class Sha256TokenCodec implements TokenCodec {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public TokenValue generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenValue(rawToken, hash(rawToken));
    }

    @Override
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
