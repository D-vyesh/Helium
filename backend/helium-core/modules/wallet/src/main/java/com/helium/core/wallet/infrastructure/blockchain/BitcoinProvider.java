package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.domain.DepositAddress;
import com.helium.core.wallet.domain.DepositAddressStatus;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.DepositAddressRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BitcoinProvider implements BlockchainProvider {
    private static final Logger log = LoggerFactory.getLogger(BitcoinProvider.class);

    private final BitcoinRpcClient rpcClient;
    private final com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo;
    private final HdAddressGenerator hdGenerator;
    private final DepositAddressRepository addressRepository;

    public BitcoinProvider(BitcoinRpcClient rpcClient, 
                           com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo,
                           HdAddressGenerator hdGenerator,
                           DepositAddressRepository addressRepository) {
        this.rpcClient = rpcClient;
        this.hdWalletRepo = hdWalletRepo;
        this.hdGenerator = hdGenerator;
        this.addressRepository = addressRepository;
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
        log.debug("Scanning BTC blocks {} to {}", startBlock, endBlock);
        return rpcClient.scanBlockRange(startBlock, endBlock, activeBitcoinAddresses());
    }

    @Override
    public byte[] buildAndSignWithdrawal(Withdrawal withdrawal) {
        throw new UnsupportedOperationException(
            "BTC withdrawal signing requires PSBT construction and a configured signer"
        );
    }

    @Override
    public String broadcastTransaction(byte[] signedTx) {
        log.info("Broadcasting BTC transaction via Bitcoin Core RPC");
        return rpcClient.sendRawTransaction(signedTx);
    }

    @Override
    public long getConfirmations(String txHash) {
        return rpcClient.getConfirmations(txHash);
    }

    @Override
    public long getLatestBlockHeight() {
        return rpcClient.getBlockCount();
    }

    @Override
    public CanonicalBlockReference getCanonicalBlock(long height) {
        return rpcClient.getCanonicalBlock(height);
    }

    private Map<String, String> activeBitcoinAddresses() {
        return addressRepository.findAll().stream()
            .filter(address -> "BTC".equals(address.networkCode()))
            .filter(address -> "BTC".equals(address.assetCode()))
            .filter(address -> address.status() == DepositAddressStatus.ACTIVE)
            .collect(Collectors.toMap(address -> normalizeAddress(address.address()), DepositAddress::address, (left, right) -> left));
    }

    private static String normalizeAddress(String address) {
        return address == null ? null : address.trim().toLowerCase(Locale.ROOT);
    }
}
