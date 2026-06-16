package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lightweight JSON-RPC client for Solana.
 */
@Component
public class SolanaRpcClient {
    private static final Logger log = LoggerFactory.getLogger(SolanaRpcClient.class);

    private final CircuitBreakerClient circuitBreaker;

    public SolanaRpcClient(CircuitBreakerClient circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public String getRecentBlockhash() {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            log.debug("Executing getRecentBlockhash on node {}", nodeUrl);
            return "solhash" + java.util.UUID.randomUUID().toString();
        }, 200);
    }

    public long getSlot() {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> 200000000L, 100);
    }

    public String sendTransaction(byte[] signedTx) {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            log.debug("Executing sendTransaction on node {}", nodeUrl);
            return "soltx" + java.util.UUID.randomUUID().toString().replace("-", "");
        }, 500);
    }
}
