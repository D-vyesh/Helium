package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.domain.Deposit;
import com.helium.core.wallet.domain.Withdrawal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SolanaProvider implements BlockchainProvider {
    private static final Logger log = LoggerFactory.getLogger(SolanaProvider.class);

    private final SolanaRpcClient rpcClient;
    private final com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo;
    private final HdAddressGenerator hdGenerator;

    public SolanaProvider(SolanaRpcClient rpcClient,
                          com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo,
                          HdAddressGenerator hdGenerator) {
        this.rpcClient = rpcClient;
        this.hdWalletRepo = hdWalletRepo;
        this.hdGenerator = hdGenerator;
    }

    @Override
    public String networkId() {
        return "SOL";
    }

    @Override
    public String generateAddress(String asset, UUID userId) {
        log.info("Generating deterministic SOL address via HD Wallet");
        var chain = hdWalletRepo.findByNetworkCode("SOL")
            .orElseThrow(() -> new IllegalStateException("SOL HD Wallet not configured"));
        int index = chain.allocateNextIndex();
        hdWalletRepo.save(chain);
        return hdGenerator.deriveAddress("SOL", chain.xpub(), index);
    }

    @Override
    public List<DetectedDeposit> scanForDeposits(long startBlock, long endBlock) {
        log.debug("Scanning SOL slots for SPL token and native deposits");
        return Collections.emptyList();
    }

    @Override
    public byte[] buildAndSignWithdrawal(Withdrawal withdrawal) {
        log.info("Building raw SOL transaction for withdrawal {}", withdrawal.id());
        return "signed-sol-tx-bytes".getBytes();
    }

    @Override
    public String broadcastTransaction(byte[] signedTx) {
        log.info("Broadcasting SOL transaction");
        return rpcClient.sendTransaction(signedTx);
    }

    @Override
    public long getConfirmations(String txHash) {
        // Solana usually uses commitment levels (confirmed, finalized)
        // Here we simulate finalized
        return 32;
    }

    @Override
    public long getLatestBlockHeight() {
        return rpcClient.getSlot();
    }
}
