package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.SignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.blockchain.BitcoinRpcClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class BitcoinBroadcaster implements BlockchainBroadcastService {
    private final BitcoinRpcClient rpcClient;
    private final Clock clock;

    public BitcoinBroadcaster(BitcoinRpcClient rpcClient, Clock clock) {
        this.rpcClient = rpcClient;
        this.clock = clock;
    }

    @Override
    public String assetCode() {
        return "BTC";
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction tx) {
        if (!"SIGNED_PSBT".equals(tx.format())) {
            throw new WalletValidationException("BTC broadcaster requires a signed PSBT");
        }
        Instant start = clock.instant();
        BitcoinRpcClient.FinalizedPsbt finalized = rpcClient.finalizePsbt(tx.serializedPayload());
        String txHash = rpcClient.sendRawTransaction(finalized.rawTransactionHex().getBytes(StandardCharsets.US_ASCII));
        Instant now = clock.instant();
        return new BroadcastResult(
            tx.withdrawalId(),
            tx.id(),
            tx.assetCode(),
            tx.networkCode(),
            txHash,
            "BITCOIN_CORE",
            "bitcoin-core-rpc",
            null,
            Duration.between(start, now),
            txHash,
            now
        );
    }
}
