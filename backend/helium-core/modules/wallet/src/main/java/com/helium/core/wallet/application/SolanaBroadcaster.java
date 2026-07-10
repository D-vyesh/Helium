package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.SignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.blockchain.SolanaRpcClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SolanaBroadcaster implements BlockchainBroadcastService {
    private final SolanaRpcClient rpcClient;
    private final Clock clock;
    private final boolean skipPreflight;
    private final String commitment;

    public SolanaBroadcaster(
        SolanaRpcClient rpcClient,
        Clock clock,
        @Value("${helium.wallet.solana.broadcast.skip-preflight:false}") boolean skipPreflight,
        @Value("${helium.wallet.solana.broadcast.commitment:processed}") String commitment
    ) {
        this.rpcClient = rpcClient;
        this.clock = clock;
        this.skipPreflight = skipPreflight;
        this.commitment = commitment;
    }

    @Override
    public String assetCode() {
        return "SOL";
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction tx) {
        if (!"SIGNED_SOLANA_V0".equals(tx.format())) {
            throw new WalletValidationException("SOL broadcaster requires a signed versioned transaction");
        }
        byte[] signedTransaction;
        try {
            signedTransaction = Base64.getDecoder().decode(tx.serializedPayload());
        } catch (IllegalArgumentException exception) {
            throw new WalletValidationException("SOL signed transaction payload must be base64");
        }
        Instant start = clock.instant();
        String signature = rpcClient.sendTransaction(signedTransaction, skipPreflight, commitment);
        long slot = rpcClient.getSlot();
        Instant now = clock.instant();
        return new BroadcastResult(
            tx.withdrawalId(),
            tx.id(),
            tx.assetCode(),
            tx.networkCode(),
            signature,
            "SOLANA_JSON_RPC",
            "solana-rpc-slot-" + slot,
            null,
            Duration.between(start, now),
            Long.toString(slot),
            now
        );
    }
}
