package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.SignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.blockchain.EthereumRpcClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class EthereumBroadcaster implements BlockchainBroadcastService {
    private final EthereumRpcClient rpcClient;
    private final Clock clock;

    public EthereumBroadcaster(EthereumRpcClient rpcClient, Clock clock) {
        this.rpcClient = rpcClient;
        this.clock = clock;
    }

    @Override
    public String assetCode() {
        return "ETH";
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction tx) {
        if (!"SIGNED_EIP1559".equals(tx.format())) {
            throw new WalletValidationException("ETH broadcaster requires a signed EIP-1559 transaction");
        }
        Instant start = clock.instant();
        String txHash = rpcClient.sendRawTransaction(tx.serializedPayload());
        Instant now = clock.instant();
        return new BroadcastResult(
            tx.withdrawalId(),
            tx.id(),
            tx.assetCode(),
            tx.networkCode(),
            txHash,
            "ETHEREUM_JSON_RPC",
            "ethereum-rpc",
            null,
            Duration.between(start, now),
            txHash,
            now
        );
    }
}
