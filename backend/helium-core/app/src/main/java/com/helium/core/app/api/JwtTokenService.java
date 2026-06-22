package com.helium.core.app.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.authuser.domain.Role;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;

    public JwtTokenService(
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${helium.auth.jwt-secret:local-development-jwt-secret-change-me-minimum-32-bytes}") String jwtSecret
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("helium.auth.jwt-secret must be at least 32 bytes");
        }
    }

    public IssuedAccessToken issue(UUID userId, Set<Role> roles) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);
        List<String> roleNames = roles.stream().map(Enum::name).sorted().toList();
        String token = sign(Map.of("alg", "HS256", "typ", "JWT"), Map.of(
            "sub", userId.toString(),
            "roles", roleNames,
            "iat", issuedAt.getEpochSecond(),
            "exp", expiresAt.getEpochSecond(),
            "typ", "access"
        ));
        return new IssuedAccessToken(token, expiresAt);
    }

    public Optional<AccessTokenClaims> validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String signedContent = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(signature(signedContent), BASE64_URL_DECODER.decode(parts[2]))) {
                return Optional.empty();
            }
            Map<String, Object> claims = objectMapper.readValue(
                BASE64_URL_DECODER.decode(parts[1]),
                new TypeReference<>() {}
            );
            if (!"access".equals(claims.get("typ"))) {
                return Optional.empty();
            }
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (!Instant.ofEpochSecond(expiresAt).isAfter(clock.instant())) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString((String) claims.get("sub"));
            @SuppressWarnings("unchecked")
            List<String> roleNames = (List<String>) claims.get("roles");
            Set<Role> roles = roleNames.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
            return Optional.of(new AccessTokenClaims(userId, roles));
        } catch (RuntimeException exception) {
            return Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String sign(Map<String, Object> header, Map<String, Object> claims) {
        try {
            String encodedHeader = BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedClaims = BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signedContent = encodedHeader + "." + encodedClaims;
            return signedContent + "." + BASE64_URL_ENCODER.encodeToString(signature(signedContent));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT could not be issued", exception);
        }
    }

    private byte[] signature(String signedContent) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {}

    public record AccessTokenClaims(UUID userId, Set<Role> roles) {}
}
