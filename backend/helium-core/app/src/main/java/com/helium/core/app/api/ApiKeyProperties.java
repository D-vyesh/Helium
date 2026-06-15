package com.helium.core.app.api;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "helium.api-keys")
public record ApiKeyProperties(
    String currentVersion,
    String currentPepper,
    String previousVersion,
    String previousPepper,
    Duration signatureTtl,
    Duration nonceTtl,
    Duration defaultLifetime,
    List<String> allowedClockSkewHeaders
) {
    public ApiKeyProperties {
        if (currentVersion == null || currentVersion.isBlank()) {
            currentVersion = "v1";
        }
        if (currentPepper == null || currentPepper.isBlank()) {
            currentPepper = "local-development-api-key-pepper-change-me";
        }
        if (signatureTtl == null) {
            signatureTtl = Duration.ofMinutes(5);
        }
        if (nonceTtl == null) {
            nonceTtl = Duration.ofMinutes(10);
        }
        if (defaultLifetime == null) {
            defaultLifetime = Duration.ofDays(90);
        }
        if (allowedClockSkewHeaders == null) {
            allowedClockSkewHeaders = List.of("X-API-Timestamp");
        }
    }
}
