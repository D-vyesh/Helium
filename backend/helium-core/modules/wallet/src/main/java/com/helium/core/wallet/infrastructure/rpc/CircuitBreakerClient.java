package com.helium.core.wallet.infrastructure.rpc;

import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool.RpcProviderState;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreakerClient {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerClient.class);

    private final BlockchainProviderPool providerPool;

    public CircuitBreakerClient(BlockchainProviderPool providerPool) {
        this.providerPool = providerPool;
    }

    /**
     * Executes a call with hedging. If the primary node doesn't respond within the 
     * latency threshold, a secondary request is fired to the backup node, and the 
     * fastest successful response wins.
     */
    public <T> T executeWithHedging(String networkId, RpcCall<T> call, long p95LatencyMs) {
        return executeWithHedging(networkId, call, p95LatencyMs, false);
    }

    public <T> T executeLatestBlockWithHedging(String networkId, RpcCall<T> call, long p95LatencyMs) {
        return executeWithHedging(networkId, call, p95LatencyMs, true);
    }

    public <T> T executeWithFailover(String networkId, RpcCall<T> call) {
        RuntimeException lastFailure = null;
        for (RpcProviderState provider : providerPool.eligibleProvidersForExecution(networkId)) {
            try {
                return executeOnProvider(provider, call, false);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                providerPool.recordSwitch();
            }
        }
        throw lastFailure == null
            ? new IllegalStateException("No healthy RPC providers available for " + networkId)
            : lastFailure;
    }

    public <T> T executeOnPrimary(String networkId, RpcCall<T> call) {
        return executeOnProvider(providerPool.primary(networkId), call, false);
    }

    public <T> java.util.List<RpcProviderCallResult<T>> executeOnEligibleProviders(String networkId, RpcCall<T> call) {
        return executeOnEligibleProviders(networkId, (providerId, nodeUrl) -> call.execute(nodeUrl));
    }

    public <T> java.util.List<RpcProviderCallResult<T>> executeOnEligibleProviders(String networkId, ProviderRpcCall<T> call) {
        java.util.List<RpcProviderCallResult<T>> results = new java.util.ArrayList<>();
        for (RpcProviderState provider : providerPool.eligibleProvidersForExecution(networkId)) {
            try {
                results.add(RpcProviderCallResult.success(provider.id(), executeOnProvider(provider, nodeUrl -> call.execute(provider.id(), nodeUrl), false)));
            } catch (RuntimeException exception) {
                results.add(RpcProviderCallResult.failure(provider.id(), exception));
            }
        }
        return java.util.List.copyOf(results);
    }

    private <T> T executeWithHedging(String networkId, RpcCall<T> call, long p95LatencyMs, boolean resultIsLatestBlock) {
        RpcProviderState primary = providerPool.primary(networkId);

        CompletableFuture<T> primaryFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return executeOnProvider(primary, call, resultIsLatestBlock);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            // Wait for P95 latency. If it succeeds before this, great.
            return primaryFuture.get(p95LatencyMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            providerPool.recordTimeout(primary);
            log.warn("RPC primary {} exceeded P95 latency ({}ms). Hedging request...", primary.id(), p95LatencyMs);
            // Fire secondary
            RpcProviderState secondary = providerPool.secondary(networkId, primary.id()).orElse(null);
            if (secondary == null) {
                log.warn("No secondary node available. Waiting for primary...");
                return primaryFuture.join(); // fallback to waiting
            }
            providerPool.recordSwitch();

            CompletableFuture<T> secondaryFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeOnProvider(secondary, call, resultIsLatestBlock);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            // Return whoever finishes first successfully
            return firstSuccessful(primaryFuture, secondaryFuture);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Primary RPC failed immediately. Trying secondary.", e);
            RpcProviderState secondary = providerPool.secondary(networkId, primary.id()).orElse(null);
            if (secondary != null) {
                providerPool.recordSwitch();
                try {
                    return executeOnProvider(secondary, call, resultIsLatestBlock);
                } catch (Exception ex) {
                    throw new RuntimeException("Both primary and secondary RPC nodes failed", ex);
                }
            }
            throw new RuntimeException("Primary failed and no secondary available", e);
        }
    }

    private <T> T executeOnProvider(RpcProviderState provider, RpcCall<T> call, boolean resultIsLatestBlock) {
        Instant start = Instant.now();
        try {
            T result = call.execute(provider.url());
            providerPool.recordSuccess(provider, Duration.between(start, Instant.now()), resultIsLatestBlock ? latestBlock(result) : null);
            return result;
        } catch (Exception exception) {
            providerPool.recordFailure(provider, exception, Duration.between(start, Instant.now()));
            throw exception instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(exception);
        }
    }

    private static Long latestBlock(Object result) {
        if (result instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static <T> T firstSuccessful(CompletableFuture<T> first, CompletableFuture<T> second) {
        try {
            return CompletableFuture.anyOf(first, second).thenApply(result -> (T) result).join();
        } catch (CompletionException exception) {
            if (!first.isCompletedExceptionally() && first.isDone()) {
                return first.join();
            }
            if (!second.isCompletedExceptionally() && second.isDone()) {
                return second.join();
            }
            if (!first.isDone()) {
                return first.join();
            }
            if (!second.isDone()) {
                return second.join();
            }
            throw exception;
        }
    }

    @FunctionalInterface
    public interface RpcCall<T> {
        T execute(String nodeUrl) throws Exception;
    }

    @FunctionalInterface
    public interface ProviderRpcCall<T> {
        T execute(String providerId, String nodeUrl) throws Exception;
    }

    public record RpcProviderCallResult<T>(
        String providerId,
        T value,
        RuntimeException failure
    ) {
        public static <T> RpcProviderCallResult<T> success(String providerId, T value) {
            return new RpcProviderCallResult<>(providerId, value, null);
        }

        public static <T> RpcProviderCallResult<T> failure(String providerId, RuntimeException failure) {
            return new RpcProviderCallResult<>(providerId, null, failure);
        }

        public boolean success() {
            return failure == null;
        }
    }
}
