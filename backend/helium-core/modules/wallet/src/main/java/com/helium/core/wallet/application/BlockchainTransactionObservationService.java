package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.infrastructure.blockchain.BitcoinRpcClient;
import com.helium.core.wallet.infrastructure.blockchain.EthereumRpcClient;
import com.helium.core.wallet.infrastructure.blockchain.SolanaRpcClient;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BlockchainTransactionObservationService {
    private final CircuitBreakerClient circuitBreaker;
    private final BitcoinRpcClient bitcoinRpcClient;
    private final EthereumRpcClient ethereumRpcClient;
    private final SolanaRpcClient solanaRpcClient;
    private final Clock clock;

    public BlockchainTransactionObservationService(
        CircuitBreakerClient circuitBreaker,
        BitcoinRpcClient bitcoinRpcClient,
        EthereumRpcClient ethereumRpcClient,
        SolanaRpcClient solanaRpcClient,
        Clock clock
    ) {
        this.circuitBreaker = circuitBreaker;
        this.bitcoinRpcClient = bitcoinRpcClient;
        this.ethereumRpcClient = ethereumRpcClient;
        this.solanaRpcClient = solanaRpcClient;
        this.clock = clock;
    }

    public List<BlockchainTransactionObservation> observe(String network, String transactionId) {
        String normalized = BlockchainNetwork.normalizeNetworkCode(network);
        Instant observedAt = clock.instant();
        return circuitBreaker.executeOnEligibleProviders(normalized, (providerId, nodeUrl) ->
            observeProvider(normalized, providerId, nodeUrl, transactionId, observedAt)
        ).stream()
            .map(result -> result.success()
                ? result.value()
                : failedObservation(normalized, transactionId, result.providerId(), result.failure(), observedAt))
            .toList();
    }

    private BlockchainTransactionObservation observeProvider(
        String network,
        String providerId,
        String nodeUrl,
        String transactionId,
        Instant observedAt
    ) throws Exception {
        return switch (network) {
            case "BTC" -> bitcoinRpcClient.observeTransaction(nodeUrl, providerId, transactionId, observedAt);
            case "ETH" -> ethereumRpcClient.observeTransaction(nodeUrl, providerId, transactionId, observedAt);
            case "SOL" -> solanaRpcClient.observeTransaction(nodeUrl, providerId, transactionId, observedAt);
            default -> throw new IllegalArgumentException("Unsupported blockchain network for observation: " + network);
        };
    }

    private static BlockchainTransactionObservation failedObservation(
        String network,
        String transactionId,
        String providerId,
        RuntimeException failure,
        Instant observedAt
    ) {
        String message = failure == null || failure.getMessage() == null ? "RPC_ERROR" : failure.getMessage();
        return new BlockchainTransactionObservation(
            network,
            transactionId,
            providerId,
            false,
            false,
            null,
            null,
            null,
            "RPC_ERROR:" + message.substring(0, Math.min(120, message.length())),
            observedAt
        );
    }
}
