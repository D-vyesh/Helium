package com.helium.core.wallet.infrastructure.rpc;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RpcNodeManager {
    private static final Logger log = LoggerFactory.getLogger(RpcNodeManager.class);

    // Network -> List of RPC Endpoints
    private final Map<String, List<String>> networkNodes = new ConcurrentHashMap<>();
    
    // Node URL -> Health Score (0-100, 100 is best)
    private final Map<String, Integer> nodeHealth = new ConcurrentHashMap<>();
    
    // Node URL -> Last Failure Time
    private final Map<String, Instant> lastFailure = new ConcurrentHashMap<>();

    public RpcNodeManager() {
        // In a real setup, these would come from properties/config
        networkNodes.put("ETH", List.of(
            "https://eth-mainnet.alchemyapi.io/v2/dummy",
            "https://mainnet.infura.io/v3/dummy",
            "https://powerful-spring-butterfly.quiknode.pro/dummy/"
        ));
        networkNodes.put("BTC", List.of(
            "http://localhost:18443",
            "https://btc.quicknode.pro/dummy/"
        ));
        networkNodes.put("SOL", List.of(
            "https://api.mainnet-beta.solana.com",
            "https://solana-mainnet.alchemyapi.io/v2/dummy"
        ));

        // Initialize all nodes to max health
        networkNodes.values().forEach(nodes -> nodes.forEach(node -> nodeHealth.put(node, 100)));
    }

    public String getPrimaryNode(String networkId) {
        return networkNodes.getOrDefault(networkId, List.of()).stream()
            .filter(this::isNodeEligible)
            .max(Comparator.comparingInt(nodeHealth::get))
            .orElseThrow(() -> new IllegalStateException("No healthy RPC nodes available for " + networkId));
    }

    public String getSecondaryNode(String networkId, String excludeNode) {
        return networkNodes.getOrDefault(networkId, List.of()).stream()
            .filter(node -> !node.equals(excludeNode))
            .filter(this::isNodeEligible)
            .max(Comparator.comparingInt(nodeHealth::get))
            .orElse(null);
    }

    public void recordSuccess(String node) {
        nodeHealth.computeIfPresent(node, (k, v) -> Math.min(100, v + 2));
    }

    public void recordFailure(String node) {
        log.warn("RPC Failure recorded for node: {}", node);
        nodeHealth.computeIfPresent(node, (k, v) -> Math.max(0, v - 20));
        lastFailure.put(node, Instant.now());
    }

    private boolean isNodeEligible(String node) {
        int health = nodeHealth.getOrDefault(node, 0);
        if (health > 20) return true;
        
        // If severely degraded, only retry if 5 minutes have passed
        Instant failureTime = lastFailure.get(node);
        return failureTime != null && Duration.between(failureTime, Instant.now()).toMinutes() > 5;
    }
}
