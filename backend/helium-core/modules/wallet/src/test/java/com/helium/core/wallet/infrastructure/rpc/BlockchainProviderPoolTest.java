package com.helium.core.wallet.infrastructure.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BlockchainProviderPoolTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void selectsSecondaryProviderAfterPrimaryCircuitOpens() {
        BlockchainProviderPool pool = pool(Map.of(
            "helium.wallet.rpc.eth.nodes", "https://primary.example,https://secondary.example"
        ));
        CircuitBreakerClient client = new CircuitBreakerClient(pool);

        assertThatThrownBy(() -> client.executeOnPrimary("ETH", node -> {
            throw new IllegalStateException("timeout");
        })).isInstanceOf(RuntimeException.class);

        String result = client.executeWithFailover("ETH", node -> "ok:" + node);

        assertThat(result).isEqualTo("ok:https://secondary.example");
    }

    @Test
    void manualDisableRemovesProviderFromSelectionUntilEnabled() {
        BlockchainProviderPool pool = pool(Map.of(
            "helium.wallet.rpc.btc.nodes", "http://node-a,http://node-b"
        ));
        String firstProvider = pool.primary("BTC").id();

        pool.disable(firstProvider);

        assertThat(pool.primary("BTC").id()).isNotEqualTo(firstProvider);

        pool.enable(firstProvider);

        assertThat(pool.providers("BTC")).anyMatch(provider -> provider.id().equals(firstProvider)
            && provider.circuitState() == RpcCircuitState.HALF_OPEN);
    }

    @Test
    void staleBlockHeightDegradesLaggingProvider() {
        BlockchainProviderPool pool = pool(Map.of(
            "helium.wallet.rpc.sol.nodes", "https://sol-a,https://sol-b"
        ));
        var providers = pool.providers("SOL");

        pool.recordSuccess(providers.get(0), Duration.ofMillis(10), 100L);
        pool.recordSuccess(providers.get(1), Duration.ofMillis(10), 90L);

        assertThat(providers.get(1).healthState()).isEqualTo(RpcProviderHealthState.DEGRADED);
    }

    @Test
    void rateLimitUsesRetryAfterBeforeProviderCanHalfOpen() {
        BlockchainProviderPool pool = pool(Map.of(
            "helium.wallet.rpc.eth.nodes", "https://primary.example"
        ));
        var provider = pool.providers("ETH").get(0);

        pool.recordFailure(provider, new IllegalStateException("HTTP 429 Retry-After=120"), Duration.ofMillis(5));

        assertThat(provider.circuitState()).isEqualTo(RpcCircuitState.OPEN);
        assertThat(provider.healthState()).isEqualTo(RpcProviderHealthState.DEGRADED);
        assertThat(provider.retryAfterAt()).isEqualTo(Instant.parse("2026-01-01T00:02:00Z"));
    }

    @Test
    void consistencyIssueDegradesProviderAndIsVisibleForAdmin() {
        BlockchainProviderPool pool = pool(Map.of(
            "helium.wallet.rpc.btc.nodes", "http://node-a"
        ));
        var provider = pool.providers("BTC").get(0);

        pool.markInconsistent(provider, "latest block height lags best provider by 7 blocks");

        assertThat(provider.healthState()).isEqualTo(RpcProviderHealthState.DEGRADED);
        assertThat(provider.lastConsistencyIssue()).contains("lags best provider");
    }

    private static BlockchainProviderPool pool(Map<String, String> properties) {
        MockEnvironment environment = new MockEnvironment();
        properties.forEach(environment::setProperty);
        return new BlockchainProviderPool(environment, CLOCK, new SimpleMeterRegistry(), 1, 30_000, 6);
    }
}
