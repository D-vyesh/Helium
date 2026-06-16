package com.helium.core.app.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Secret provider that supports hot-reloading from a pluggable SecretBackend.
 * Refreshes secrets on a schedule and publishes rotation events.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "helium.secrets", name = "backend")
public class VaultSecretProvider implements RotatingSecretProvider {
    private static final Logger log = LoggerFactory.getLogger(VaultSecretProvider.class);
    private static final String API_KEY_PATH = "helium/api-keys";

    private final SecretBackend backend;
    private final ApiKeyProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final AtomicReference<VersionedSecrets> cached = new AtomicReference<>();

    public VaultSecretProvider(
        SecretBackend backend,
        ApiKeyProperties properties,
        ApplicationEventPublisher eventPublisher,
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate
    ) {
        this.backend = backend;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
        this.cached.set(new VersionedSecrets(
            new SecretVersion(properties.currentVersion(), properties.currentPepper()),
            properties.previousVersion() != null && properties.previousPepper() != null
                ? new SecretVersion(properties.previousVersion(), properties.previousPepper())
                : null
        ));
        log.info("Initialized VaultSecretProvider with backend={}", backend.name());
    }

    @Override
    public SecretVersion current() {
        return cached.get().current();
    }

    @Override
    public Optional<SecretVersion> find(String version) {
        return verificationVersions().stream()
            .filter(secret -> secret.version().equals(version))
            .findFirst();
    }

    @Override
    public List<SecretVersion> verificationVersions() {
        VersionedSecrets secrets = cached.get();
        List<SecretVersion> versions = new ArrayList<>();
        versions.add(secrets.current());
        if (secrets.previous() != null) {
            versions.add(secrets.previous());
        }
        return List.copyOf(versions);
    }

    @Scheduled(fixedDelayString = "${helium.secrets.refresh-interval-ms:300000}")
    public void refresh() {
        try {
            Map<String, String> fetched = backend.fetchSecrets(API_KEY_PATH);
            String newPepper = fetched.getOrDefault("HELIUM_API_KEY_PEPPER", properties.currentPepper());
            if (newPepper.equals(cached.get().current().value())) {
                return;
            }
            String newVersion = "v" + System.currentTimeMillis();
            String oldVersion = cached.get().current().version();
            SecretVersion previous = cached.get().current();
            SecretVersion next = new SecretVersion(newVersion, newPepper);
            cached.set(new VersionedSecrets(next, previous));

            log.info("Secret rotation detected: {} -> {}", oldVersion, newVersion);
            eventPublisher.publishEvent(new SecretRotationEvent(this, API_KEY_PATH, oldVersion, newVersion));
            
            // Record audit log for secret rotation
            try {
                jdbcTemplate.update(
                    "INSERT INTO security_audit_events (id, event_type, actor_id, metadata, created_at) VALUES (?, ?, ?, ?::jsonb, ?)",
                    java.util.UUID.randomUUID(),
                    "SECURITY.SECRET_ROTATED",
                    "system",
                    String.format("{\"path\":\"%s\", \"old_version\":\"%s\", \"new_version\":\"%s\"}", API_KEY_PATH, oldVersion, newVersion),
                    java.time.Instant.now()
                );
            } catch (Exception e) {
                log.error("Failed to write audit log for secret rotation", e);
            }
        } catch (RuntimeException exception) {
            log.error("Secret refresh failed from backend={}: {}", backend.name(), exception.getMessage());
        }
    }

    private record VersionedSecrets(SecretVersion current, SecretVersion previous) {}
}
