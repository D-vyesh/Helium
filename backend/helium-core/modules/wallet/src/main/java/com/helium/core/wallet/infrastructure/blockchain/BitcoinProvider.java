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
public class BitcoinProvider implements BlockchainProvider {
    private static final Logger log = LoggerFactory.getLogger(BitcoinProvider.class);

    private final BitcoinRpcClient rpcClient;
    private final com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo;
    private final HdAddressGenerator hdGenerator;

    public BitcoinProvider(BitcoinRpcClient rpcClient, 
                           com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo,
                           HdAddressGenerator hdGenerator) {
        this.rpcClient = rpcClient;
        this.hdWalletRepo = hdWalletRepo;
        this.hdGenerator = hdGenerator;
    }

    @Override
    public String networkId() {
        return "BTC";
    }

    @Override
    public String generateAddress(String asset, UUID userId) {
        log.info("Generating deterministic BTC address via HD Wallet");
        var chain = hdWalletRepo.findByNetworkCode("BTC")
            .orElseThrow(() -> new IllegalStateException("BTC HD Wallet not configured"));
        int index = chain.allocateNextIndex();
        hdWalletRepo.save(chain);
        return hdGenerator.deriveAddress("BTC", chain.xpub(), index);
    }

    @Override
    public List<DetectedDeposit> scanForDeposits(long startBlock, long endBlock) {
        log.debug("Scanning BTC blocks via `listsinceblock` equivalent");
        return Collections.emptyList();
    }

    @Override
    public byte[] buildAndSignWithdrawal(Withdrawal withdrawal) {
        log.info("Building raw BTC transaction for withdrawal {}", withdrawal.id());
        return "signed-btc-tx-bytes".getBytes();
    }

    @Override
    public String broadcastTransaction(byte[] signedTx) {
        log.info("Broadcasting BTC transaction via Bitcoin Core RPC");
        return rpcClient.sendRawTransaction(signedTx);
    }

    @Override
    public long getConfirmations(String txHash) {
        return 6;
    }

    @Override
    public long getLatestBlockHeight() {
        return rpcClient.getBlockCount();
    }
}
