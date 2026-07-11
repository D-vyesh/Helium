package com.helium.core.wallet.infrastructure.rpc;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BlockchainProviderPool {
    private final Map<String, List<RpcProviderState>> providersByChain = new ConcurrentHashMap<>();
    private final Map<String, RpcProviderState> providersById = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int failureThreshold;
    private final Duration recoveryTimeout;
    private final long staleBlockLag;
    private final Counter providerSwitches;
    private final Counter staleResponses;
    private final Counter requestCount;
    private final Counter errorCount;
    private final Counter timeoutCount;
    private final Timer rpcLatency;

    public BlockchainProviderPool(
        Environment environment,
        Clock clock,
        MeterRegistry meterRegistry,
        @Value("${helium.wallet.rpc.failure-threshold:3}") int failureThreshold,
        @Value("${helium.wallet.rpc.recovery-timeout-ms:30000}") long recoveryTimeoutMs,
        @Value("${helium.wallet.rpc.stale-block-lag:6}") long staleBlockLag
    ) {
        this.clock = clock;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryTimeout = Duration.ofMillis(Math.max(1_000L, recoveryTimeoutMs));
        this.staleBlockLag = Math.max(1L, staleBlockLag);
        this.providerSwitches = Counter.builder("helium_wallet_rpc_provider_switches_total").register(meterRegistry);
        this.staleResponses = Counter.builder("helium_wallet_rpc_stale_responses_total").register(meterRegistry);
        this.requestCount = Counter.builder("helium_wallet_rpc_requests_total").register(meterRegistry);
        this.errorCount = Counter.builder("helium_wallet_rpc_errors_total").register(meterRegistry);
        this.timeoutCount = Counter.builder("helium_wallet_rpc_timeouts_total").register(meterRegistry);
        this.rpcLatency = Timer.builder("helium_wallet_rpc_latency_seconds").register(meterRegistry);
        register("BTC", environment.getProperty("helium.wallet.rpc.btc.nodes", ""));
        register("ETH", environment.getProperty("helium.wallet.rpc.eth.nodes", ""));
        register("SOL", environment.getProperty("helium.wallet.rpc.sol.nodes", ""));
    }

    public RpcProviderState primary(String chain) {
        List<RpcProviderState> eligible = eligibleProviders(chain);
        if (eligible.isEmpty()) {
            throw new IllegalStateException("No healthy RPC providers available for " + chain);
        }
        return eligible.get(0);
    }

    public Optional<RpcProviderState> secondary(String chain, String excludeProviderId) {
        return eligibleProviders(chain).stream()
            .filter(provider -> !provider.id().equals(excludeProviderId))
            .findFirst();
    }

    public List<RpcProviderState> providers(String chain) {
        return List.copyOf(providersByChain.getOrDefault(normalizeChain(chain), List.of()));
    }

    public List<RpcProviderState> eligibleProvidersForExecution(String chain) {
        return eligibleProviders(chain);
    }

    public List<RpcProviderState> providers() {
        return providersByChain.values().stream().flatMap(List::stream).toList();
    }

    public void disable(String providerId) {
        provider(providerId).manualDisable(clock.instant());
    }

    public void enable(String providerId) {
        provider(providerId).manualEnable();
    }

    public void recordSuccess(RpcProviderState provider, Duration latency, Long observedBlockHeight) {
        requestCount.increment();
        rpcLatency.record(latency);
        provider.recordSuccess(latency, observedBlockHeight, clock.instant());
        detectStaleProvider(provider);
    }

    public void recordFailure(RpcProviderState provider, Throwable failure, Duration latency) {
        requestCount.increment();
        errorCount.increment();
        rpcLatency.record(latency);
        provider.recordFailure(failure, clock.instant(), failureThreshold, recoveryTimeout);
    }

    public void recordTimeout(RpcProviderState provider) {
        timeoutCount.increment();
        provider.recordTimeout(clock.instant(), failureThreshold, recoveryTimeout);
    }

    public void recordSwitch() {
        providerSwitches.increment();
    }

    public boolean isRetryableRateLimit(Throwable failure) {
        String message = failure.getMessage();
        return message != null && (message.contains("HTTP 429") || message.toLowerCase(Locale.ROOT).contains("rate limit"));
    }

    private List<RpcProviderState> eligibleProviders(String chain) {
        return providers(normalizeChain(chain)).stream()
            .filter(provider -> provider.isEligible(clock.instant(), recoveryTimeout))
            .sorted(Comparator.comparingInt(RpcProviderState::score).reversed())
            .toList();
    }

    private RpcProviderState provider(String providerId) {
        RpcProviderState provider = providersById.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("RPC provider was not found: " + providerId);
        }
        return provider;
    }

    private void detectStaleProvider(RpcProviderState provider) {
        Long height = provider.latestBlockHeight();
        if (height == null) {
            return;
        }
        long best = providers(provider.chain()).stream()
            .map(RpcProviderState::latestBlockHeight)
            .filter(value -> value != null)
            .mapToLong(Long::longValue)
            .max()
            .orElse(height);
        if (best - height >= staleBlockLag) {
            staleResponses.increment();
            provider.markStale(clock.instant());
        }
    }

    private void register(String chain, String rawNodes) {
        String normalizedChain = normalizeChain(chain);
        List<RpcProviderState> states = parseNodes(normalizedChain, rawNodes);
        providersByChain.put(normalizedChain, states);
        states.forEach(state -> providersById.put(state.id(), state));
    }

    private static List<RpcProviderState> parseNodes(String chain, String rawNodes) {
        if (rawNodes == null || rawNodes.isBlank()) {
            return List.of();
        }
        String[] entries = rawNodes.split(",");
        List<RpcProviderState> states = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            String url = entries[i].trim();
            if (url.isBlank()) {
                continue;
            }
            String id = chain.toLowerCase(Locale.ROOT) + "-" + providerName(url) + "-" + (states.size() + 1);
            states.add(new RpcProviderState(id, chain, url));
        }
        return List.copyOf(states);
    }

    private static String providerName(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return "local";
            }
            String normalized = host.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
        } catch (IllegalArgumentException exception) {
            return "provider";
        }
    }

    private static String normalizeChain(String chain) {
        return chain == null ? "" : chain.trim().toUpperCase(Locale.ROOT);
    }

    public static final class RpcProviderState {
        private final String id;
        private final String chain;
        private final String url;
        private volatile boolean manuallyDisabled;
        private volatile RpcProviderHealthState healthState = RpcProviderHealthState.HEALTHY;
        private volatile RpcCircuitState circuitState = RpcCircuitState.CLOSED;
        private volatile int consecutiveFailures;
        private volatile long requestCount;
        private volatile long failureCount;
        private volatile long timeoutCount;
        private volatile Duration lastLatency = Duration.ZERO;
        private volatile Long latestBlockHeight;
        private volatile Instant lastSuccessAt;
        private volatile Instant lastFailureAt;
        private volatile Instant openedAt;
        private volatile Instant retryAfterAt;
        private volatile Instant disabledAt;
        private volatile String lastConsistencyIssue;

        private RpcProviderState(String id, String chain, String url) {
            this.id = id;
            this.chain = chain;
            this.url = url;
        }

        public String id() { return id; }
        public String chain() { return chain; }
        public String url() { return url; }
        public boolean manuallyDisabled() { return manuallyDisabled; }
        public RpcProviderHealthState healthState() { return healthState; }
        public RpcCircuitState circuitState() { return circuitState; }
        public int consecutiveFailures() { return consecutiveFailures; }
        public long requestCount() { return requestCount; }
        public long failureCount() { return failureCount; }
        public long timeoutCount() { return timeoutCount; }
        public Duration lastLatency() { return lastLatency; }
        public Long latestBlockHeight() { return latestBlockHeight; }
        public Instant lastSuccessAt() { return lastSuccessAt; }
        public Instant lastFailureAt() { return lastFailureAt; }
        public Instant retryAfterAt() { return retryAfterAt; }
        public Instant disabledAt() { return disabledAt; }
        public String lastConsistencyIssue() { return lastConsistencyIssue; }

        public boolean isEligibleForExecution() {
            return !manuallyDisabled && (circuitState == RpcCircuitState.CLOSED || circuitState == RpcCircuitState.HALF_OPEN);
        }

        private int score() {
            int base = switch (healthState) {
                case HEALTHY -> 100;
                case DEGRADED -> 50;
                case UNAVAILABLE -> 0;
            };
            long latencyPenalty = Math.min(40L, lastLatency.toMillis() / 100L);
            return (int) Math.max(0, base - latencyPenalty - (consecutiveFailures * 10L));
        }

        private boolean isEligible(Instant now, Duration recoveryTimeout) {
            if (manuallyDisabled) {
                return false;
            }
            if (circuitState == RpcCircuitState.CLOSED || circuitState == RpcCircuitState.HALF_OPEN) {
                return true;
            }
            if (retryAfterAt != null) {
                if (!now.isBefore(retryAfterAt)) {
                    circuitState = RpcCircuitState.HALF_OPEN;
                    healthState = RpcProviderHealthState.DEGRADED;
                    retryAfterAt = null;
                    return true;
                }
                return false;
            }
            if (openedAt != null && Duration.between(openedAt, now).compareTo(recoveryTimeout) >= 0) {
                circuitState = RpcCircuitState.HALF_OPEN;
                healthState = RpcProviderHealthState.DEGRADED;
                return true;
            }
            return false;
        }

        private void recordSuccess(Duration latency, Long observedBlockHeight, Instant now) {
            requestCount++;
            consecutiveFailures = 0;
            lastLatency = latency == null ? Duration.ZERO : latency;
            lastSuccessAt = now;
            if (observedBlockHeight != null && observedBlockHeight >= 0) {
                latestBlockHeight = observedBlockHeight;
            }
            circuitState = RpcCircuitState.CLOSED;
            healthState = RpcProviderHealthState.HEALTHY;
            retryAfterAt = null;
            lastConsistencyIssue = null;
        }

        private void recordFailure(Throwable failure, Instant now, int failureThreshold, Duration recoveryTimeout) {
            requestCount++;
            failureCount++;
            consecutiveFailures++;
            lastFailureAt = now;
            if (failure != null && failure.getMessage() != null && failure.getMessage().contains("HTTP 429")) {
                openedAt = now;
                retryAfterAt = now.plus(parseRetryAfter(failure.getMessage(), recoveryTimeout));
                circuitState = RpcCircuitState.OPEN;
                healthState = RpcProviderHealthState.DEGRADED;
                return;
            }
            if (consecutiveFailures >= failureThreshold) {
                openedAt = now;
                circuitState = RpcCircuitState.OPEN;
                healthState = RpcProviderHealthState.UNAVAILABLE;
            } else {
                healthState = RpcProviderHealthState.DEGRADED;
            }
        }

        private void recordTimeout(Instant now, int failureThreshold, Duration recoveryTimeout) {
            timeoutCount++;
            recordFailure(new IllegalStateException("RPC timeout"), now, failureThreshold, recoveryTimeout);
        }

        private void markStale(Instant now) {
            lastFailureAt = now;
            if (healthState == RpcProviderHealthState.HEALTHY) {
                healthState = RpcProviderHealthState.DEGRADED;
            }
        }

        private void markInconsistent(String reason, Instant now) {
            lastConsistencyIssue = reason;
            lastFailureAt = now;
            if (healthState == RpcProviderHealthState.HEALTHY) {
                healthState = RpcProviderHealthState.DEGRADED;
            }
        }

        private void manualDisable(Instant now) {
            manuallyDisabled = true;
            disabledAt = now;
            circuitState = RpcCircuitState.OPEN;
            healthState = RpcProviderHealthState.UNAVAILABLE;
        }

        private void manualEnable() {
            manuallyDisabled = false;
            circuitState = RpcCircuitState.HALF_OPEN;
            healthState = RpcProviderHealthState.DEGRADED;
            consecutiveFailures = 0;
        }

        private void applyPersistedManualState(boolean disabled, Instant disabledAt) {
            if (disabled) {
                manuallyDisabled = true;
                this.disabledAt = disabledAt;
                circuitState = RpcCircuitState.OPEN;
                healthState = RpcProviderHealthState.UNAVAILABLE;
            }
        }

        private static Duration parseRetryAfter(String message, Duration fallback) {
            int marker = message.indexOf("Retry-After=");
            if (marker < 0) {
                return fallback;
            }
            int start = marker + "Retry-After=".length();
            int end = start;
            while (end < message.length() && Character.isDigit(message.charAt(end))) {
                end++;
            }
            if (end == start) {
                return fallback;
            }
            try {
                return Duration.ofSeconds(Math.max(1L, Long.parseLong(message.substring(start, end))));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }

    public void applyPersistedManualState(String providerId, boolean disabled, Instant disabledAt) {
        RpcProviderState provider = providersById.get(providerId);
        if (provider != null) {
            provider.applyPersistedManualState(disabled, disabledAt);
        }
    }

    public void markInconsistent(RpcProviderState provider, String reason) {
        provider.markInconsistent(reason, clock.instant());
    }
}
