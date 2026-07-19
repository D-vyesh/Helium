package com.helium.core.wallet.infrastructure.rpc;

import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool.RpcProviderState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RpcProviderHealthPersistence {
    private static final HexFormat HEX = HexFormat.of();

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public RpcProviderHealthPersistence(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void save(RpcProviderState provider) {
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into wallet_rpc_provider_health (
                provider_id, chain, url_hash, manually_disabled, health_state, circuit_state,
                latest_block_height, consecutive_failures, request_count, failure_count, timeout_count,
                last_latency_ms, last_success_at, last_failure_at, retry_after_at, disabled_at,
                last_consistency_issue, updated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (provider_id) do update
            set manually_disabled = excluded.manually_disabled,
                health_state = excluded.health_state,
                circuit_state = excluded.circuit_state,
                latest_block_height = excluded.latest_block_height,
                consecutive_failures = excluded.consecutive_failures,
                request_count = excluded.request_count,
                failure_count = excluded.failure_count,
                timeout_count = excluded.timeout_count,
                last_latency_ms = excluded.last_latency_ms,
                last_success_at = excluded.last_success_at,
                last_failure_at = excluded.last_failure_at,
                retry_after_at = excluded.retry_after_at,
                disabled_at = excluded.disabled_at,
                last_consistency_issue = excluded.last_consistency_issue,
                updated_at = excluded.updated_at
            """,
            provider.id(),
            provider.chain(),
            sha256(provider.url()),
            provider.manuallyDisabled(),
            provider.healthState().name(),
            provider.circuitState().name(),
            provider.latestBlockHeight(),
            provider.consecutiveFailures(),
            provider.requestCount(),
            provider.failureCount(),
            provider.timeoutCount(),
            provider.lastLatency().toMillis(),
            timestamp(provider.lastSuccessAt()),
            timestamp(provider.lastFailureAt()),
            timestamp(provider.retryAfterAt()),
            timestamp(provider.disabledAt()),
            provider.lastConsistencyIssue(),
            timestamp(now)
        );
    }

    public List<PersistedManualProviderState> loadManualProviderStates() {
        return jdbcTemplate.query(
            """
            select provider_id, manually_disabled, disabled_at
            from wallet_rpc_provider_health
            where manually_disabled = true
            """,
            (rs, rowNum) -> new PersistedManualProviderState(
                rs.getString("provider_id"),
                rs.getBoolean("manually_disabled"),
                rs.getTimestamp("disabled_at") == null ? null : rs.getTimestamp("disabled_at").toInstant()
            )
        );
    }

    private static String sha256(String value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public record PersistedManualProviderState(String providerId, boolean manuallyDisabled, Instant disabledAt) {}
}
