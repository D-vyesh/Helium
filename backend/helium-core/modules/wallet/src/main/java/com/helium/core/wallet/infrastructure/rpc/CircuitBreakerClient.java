package com.helium.core.wallet.infrastructure.rpc;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreakerClient {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerClient.class);

    private final RpcNodeManager nodeManager;

    public CircuitBreakerClient(RpcNodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    /**
     * Executes a call with hedging. If the primary node doesn't respond within the 
     * latency threshold, a secondary request is fired to the backup node, and the 
     * fastest successful response wins.
     */
    public <T> T executeWithHedging(String networkId, RpcCall<T> call, long p95LatencyMs) {
        String primary = nodeManager.getPrimaryNode(networkId);
        
        CompletableFuture<T> primaryFuture = CompletableFuture.supplyAsync(() -> {
            try {
                T result = call.execute(primary);
                nodeManager.recordSuccess(primary);
                return result;
            } catch (Exception e) {
                nodeManager.recordFailure(primary);
                throw new RuntimeException(e);
            }
        });

        try {
            // Wait for P95 latency. If it succeeds before this, great.
            return primaryFuture.get(p95LatencyMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("RPC Primary {} exceeded P95 latency ({}ms). Hedging request...", primary, p95LatencyMs);
            // Fire secondary
            String secondary = nodeManager.getSecondaryNode(networkId, primary);
            if (secondary == null) {
                log.warn("No secondary node available. Waiting for primary...");
                return primaryFuture.join(); // fallback to waiting
            }

            CompletableFuture<T> secondaryFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    T result = call.execute(secondary);
                    nodeManager.recordSuccess(secondary);
                    return result;
                } catch (Exception ex) {
                    nodeManager.recordFailure(secondary);
                    throw new RuntimeException(ex);
                }
            });

            // Return whoever finishes first successfully
            return (T) CompletableFuture.anyOf(primaryFuture, secondaryFuture).join();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Primary RPC failed immediately. Trying secondary.", e);
            String secondary = nodeManager.getSecondaryNode(networkId, primary);
            if (secondary != null) {
                try {
                    T result = call.execute(secondary);
                    nodeManager.recordSuccess(secondary);
                    return result;
                } catch (Exception ex) {
                    nodeManager.recordFailure(secondary);
                    throw new RuntimeException("Both primary and secondary RPC nodes failed", ex);
                }
            }
            throw new RuntimeException("Primary failed and no secondary available", e);
        }
    }

    @FunctionalInterface
    public interface RpcCall<T> {
        T execute(String nodeUrl) throws Exception;
    }
}
