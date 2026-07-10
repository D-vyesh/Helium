package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.domain.DepositAddress;
import com.helium.core.wallet.domain.DepositAddressStatus;
import com.helium.core.wallet.infrastructure.DepositAddressRepository;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

@Component
public class EthereumDepositScanner {
    private static final Logger log = LoggerFactory.getLogger(EthereumDepositScanner.class);
    private static final String NETWORK = "ETH";
    private static final String ASSET = "ETH";

    private final CircuitBreakerClient circuitBreaker;
    private final DepositAddressRepository addressRepository;

    public EthereumDepositScanner(CircuitBreakerClient circuitBreaker, DepositAddressRepository addressRepository) {
        this.circuitBreaker = circuitBreaker;
        this.addressRepository = addressRepository;
    }

    public List<DetectedDeposit> scan(long startBlock, long endBlock) {
        if (endBlock < startBlock) {
            return List.of();
        }
        Map<String, DepositAddress> addresses = activeEthereumAddresses();
        if (addresses.isEmpty()) {
            log.debug("EthereumDepositScanner: no active ETH deposit addresses to scan");
            return List.of();
        }
        return circuitBreaker.executeWithHedging("ETH", nodeUrl -> {
            Web3j web3j = Web3j.build(new HttpService(nodeUrl));
            try {
                log.debug("EthereumDepositScanner: scanning native ETH transfers between {} and {} on node {}", startBlock, endBlock, nodeUrl);
                List<DetectedDeposit> detected = new ArrayList<>();
                for (long height = startBlock; height <= endBlock; height++) {
                    detected.addAll(scanBlock(web3j, height, addresses));
                }
                return detected;
            } finally {
                web3j.shutdown();
            }
        }, 1000);
    }

    private List<DetectedDeposit> scanBlock(Web3j web3j, long height, Map<String, DepositAddress> addresses) throws java.io.IOException {
        EthBlock response = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(BigInteger.valueOf(height)), true).send();
        if (response.hasError()) {
            throw new IllegalStateException("eth_getBlockByNumber failed: " + response.getError().getMessage());
        }
        EthBlock.Block block = response.getBlock();
        if (block == null || block.getTransactions() == null) {
            return List.of();
        }

        List<DetectedDeposit> detected = new ArrayList<>();
        for (EthBlock.TransactionResult<?> result : block.getTransactions()) {
            Object transaction = result.get();
            if (!(transaction instanceof EthBlock.TransactionObject tx)) {
                continue;
            }
            String to = normalizeAddress(tx.getTo());
            if (to == null || !addresses.containsKey(to)) {
                continue;
            }
            BigInteger value = tx.getValue();
            if (value == null || value.signum() <= 0) {
                continue;
            }
            int outputIndex = tx.getTransactionIndex() == null ? 0 : tx.getTransactionIndex().intValueExact();
            BigDecimal amount = Convert.fromWei(new BigDecimal(value), Convert.Unit.ETHER).stripTrailingZeros();
            detected.add(new DetectedDeposit(NETWORK, tx.getHash(), outputIndex, amount, ASSET, addresses.get(to).address()));
        }
        return detected;
    }

    private Map<String, DepositAddress> activeEthereumAddresses() {
        return addressRepository.findAll().stream()
            .filter(address -> NETWORK.equals(address.networkCode()))
            .filter(address -> ASSET.equals(address.assetCode()))
            .filter(address -> address.status() == DepositAddressStatus.ACTIVE)
            .collect(Collectors.toMap(address -> normalizeAddress(address.address()), Function.identity(), (left, right) -> left));
    }

    private static String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }
}
