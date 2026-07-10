package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * Ethereum blockchain provider integration via Web3j.
 */
@Component
public class EthereumProvider implements BlockchainProvider {
    private static final Logger log = LoggerFactory.getLogger(EthereumProvider.class);

    private final EthereumDepositScanner depositScanner;
    private final CircuitBreakerClient circuitBreaker;
    private final com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo;
    private final HdAddressGenerator hdGenerator;

    public EthereumProvider(EthereumDepositScanner depositScanner, 
                            CircuitBreakerClient circuitBreaker,
                            com.helium.core.wallet.infrastructure.HdWalletChainRepository hdWalletRepo,
                            HdAddressGenerator hdGenerator) {
        this.depositScanner = depositScanner;
        this.circuitBreaker = circuitBreaker;
        this.hdWalletRepo = hdWalletRepo;
        this.hdGenerator = hdGenerator;
    }

    @Override
    public String networkId() {
        return "ETH";
    }

    @Override
    public String generateAddress(String asset, UUID userId) {
        log.info("Generating deterministic ETH deposit address for user {}", userId);
        var chain = hdWalletRepo.findByNetworkCode("ETH")
            .orElseThrow(() -> new IllegalStateException("ETH HD Wallet not configured"));
        int index = chain.allocateNextIndex();
        hdWalletRepo.save(chain);
        return hdGenerator.deriveAddress("ETH", chain.xpub(), index);
    }

    @Override
    public List<DetectedDeposit> scanForDeposits(long startBlock, long endBlock) {
        log.debug("Scanning ETH blocks {} to {}", startBlock, endBlock);
        return depositScanner.scan(startBlock, endBlock);
    }

    @Override
    public byte[] buildAndSignWithdrawal(Withdrawal withdrawal) {
        throw new UnsupportedOperationException(
            "ETH withdrawal signing requires a configured transaction builder and signer"
        );
    }

    @Override
    public String broadcastTransaction(byte[] signedTx) {
        return circuitBreaker.executeWithHedging("ETH", nodeUrl -> {
            log.info("Broadcasting ETH transaction via node {}", nodeUrl);
            Web3j web3j = Web3j.build(new HttpService(nodeUrl));
            try {
                EthSendTransaction response = web3j.ethSendRawTransaction(rawTransactionHex(signedTx)).send();
                if (response.hasError()) {
                    throw new IllegalStateException("eth_sendRawTransaction failed: " + response.getError().getMessage());
                }
                return response.getTransactionHash();
            } finally {
                web3j.shutdown();
            }
        }, 1_000);
    }

    @Override
    public long getConfirmations(String txHash) {
        return circuitBreaker.executeWithHedging("ETH", nodeUrl -> {
            Web3j web3j = Web3j.build(new HttpService(nodeUrl));
            try {
                EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
                if (receiptResponse.hasError()) {
                    throw new IllegalStateException("eth_getTransactionReceipt failed: " + receiptResponse.getError().getMessage());
                }
                Optional<TransactionReceipt> receipt = receiptResponse.getTransactionReceipt();
                if (receipt.isEmpty() || receipt.get().getBlockNumber() == null) {
                    EthTransaction transactionResponse = web3j.ethGetTransactionByHash(txHash).send();
                    if (transactionResponse.hasError()) {
                        throw new IllegalStateException("eth_getTransactionByHash failed: " + transactionResponse.getError().getMessage());
                    }
                    return transactionResponse.getTransaction().isPresent() ? 0L : -1L;
                }
                BigInteger latest = latestBlockNumber(web3j);
                BigInteger includedAt = receipt.get().getBlockNumber();
                return latest.subtract(includedAt).add(BigInteger.ONE).max(BigInteger.ZERO).longValue();
            } finally {
                web3j.shutdown();
            }
        }, 1_000);
    }

    @Override
    public long getLatestBlockHeight() {
        return circuitBreaker.executeWithHedging("ETH", nodeUrl -> {
            Web3j web3j = Web3j.build(new HttpService(nodeUrl));
            try {
                return latestBlockNumber(web3j).longValueExact();
            } finally {
                web3j.shutdown();
            }
        }, 1_000);
    }

    private static BigInteger latestBlockNumber(Web3j web3j) throws java.io.IOException {
        EthBlockNumber response = web3j.ethBlockNumber().send();
        if (response.hasError()) {
            throw new IllegalStateException("eth_blockNumber failed: " + response.getError().getMessage());
        }
        return response.getBlockNumber();
    }

    private static String rawTransactionHex(byte[] signedTx) {
        if (signedTx == null || signedTx.length == 0) {
            throw new IllegalArgumentException("signedTx is required");
        }
        String ascii = new String(signedTx, StandardCharsets.US_ASCII).trim();
        if (ascii.startsWith("0x") && Numeric.containsHexPrefix(ascii)) {
            return ascii;
        }
        return Numeric.toHexString(signedTx);
    }
}
