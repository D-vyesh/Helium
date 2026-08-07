package com.helium.core.app.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production process with the repository's safe local defaults.
 * This turns deployment placeholders into an explicit release gate instead of a
 * configuration mistake that could expose customer assets.
 */
@Component
@Profile("production")
public class ProductionReadinessValidator implements ApplicationRunner {
    private static final List<String> SENSITIVE_PROPERTIES = List.of(
        "HELIUM_JWT_SECRET",
        "HELIUM_TOTP_ENCRYPTION_KEY",
        "HELIUM_API_KEY_PEPPER"
    );

    private final Environment environment;

    public ProductionReadinessValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (String property : SENSITIVE_PROPERTIES) {
            requireSecret(property, violations);
        }
        // Third-party passwords (Neon, Upstash) — we don't control their length,
        // so we only verify they are present and not placeholders.
        requireConfigured("HELIUM_DB_PASSWORD", violations);
        requireConfigured("HELIUM_REDIS_PASSWORD", violations);
        requireEnabled("HELIUM_WALLET_CHAIN_MONITOR_ENABLED", violations);
        requireEnabled("HELIUM_WALLET_BUILDER_WORKER_ENABLED", violations);
        requireEnabled("HELIUM_CUSTODY_SIGNING_WORKER_ENABLED", violations);
        requireEnabled("HELIUM_WALLET_BROADCAST_WORKER_ENABLED", violations);
        requireEnabled("HELIUM_WALLET_CONFIRMATION_WORKER_ENABLED", violations);
        requireConfigured("HELIUM_BTC_RPC_NODES", violations);
        requireConfigured("HELIUM_ETH_RPC_NODES", violations);
        requireConfigured("HELIUM_SOL_RPC_NODES", violations);
        requireConfigured("HELIUM_SANCTIONS_BASE_URL", violations);
        requireSecret("HELIUM_SANCTIONS_API_TOKEN", violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Production readiness validation failed: " + violations.entrySet().stream()
                    .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                    .reduce((left, right) -> left + "; " + right)
                    .orElseThrow()
            );
        }
    }

    private void requireSecret(String property, Map<String, String> violations) {
        String value = environment.getProperty(property, "");
        if (value.isBlank() || value.length() < 32 || looksLikePlaceholder(value)) {
            violations.put(property, "must be a non-placeholder secret of at least 32 characters");
        }
    }

    private void requireConfigured(String property, Map<String, String> violations) {
        String value = environment.getProperty(property, "");
        if (value.isBlank() || looksLikePlaceholder(value)) {
            violations.put(property, "must be configured with a production endpoint");
        }
    }

    private void requireEnabled(String property, Map<String, String> violations) {
        if (!environment.getProperty(property, Boolean.class, false)) {
            violations.put(property, "must be enabled for production");
        }
    }

    private static boolean looksLikePlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.contains("change-me")
            || normalized.contains("local-development")
            || normalized.contains("placeholder")
            || normalized.equals("helium")
            || normalized.equals("admin");
    }
}
