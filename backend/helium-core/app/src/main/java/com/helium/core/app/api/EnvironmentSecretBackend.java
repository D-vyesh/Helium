package com.helium.core.app.api;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Default secret backend that reads from Spring Environment (env vars / application.yml).
 * This is the production-safe fallback when no external secret manager is configured.
 */
@Component
@ConditionalOnProperty(prefix = "helium.secrets", name = "backend", havingValue = "environment", matchIfMissing = true)
public class EnvironmentSecretBackend implements SecretBackend {
    private static final Map<String, String[]> PATH_MAPPINGS = Map.of(
        "helium/api-keys", new String[]{
            "HELIUM_API_KEY_PEPPER",
            "HELIUM_API_KEY_PREVIOUS_PEPPER"
        },
        "helium/database", new String[]{
            "HELIUM_DB_PASSWORD"
        },
        "helium/redis", new String[]{
            "HELIUM_REDIS_PASSWORD"
        }
    );

    private final Environment environment;

    public EnvironmentSecretBackend(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Map<String, String> fetchSecrets(String path) {
        String[] keys = PATH_MAPPINGS.getOrDefault(path, new String[0]);
        Map<String, String> secrets = new HashMap<>();
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                secrets.put(key, value);
            }
        }
        return Map.copyOf(secrets);
    }

    @Override
    public String name() {
        return "environment";
    }
}
