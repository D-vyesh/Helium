package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.application.DepositService;
import com.helium.core.wallet.application.DetectDepositCommand;
import com.helium.core.wallet.domain.Deposit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ChainMonitorJob {
    private static final Logger log = LoggerFactory.getLogger(ChainMonitorJob.class);

    private final BlockchainProviderRegistry registry;
    private final DepositService depositService;

    // Stub: In reality, we'd query the DB for the last scanned block per network
    private long lastScannedBlockEth = 18000000;
    private long lastScannedBlockBtc = 800000;

    public ChainMonitorJob(BlockchainProviderRegistry registry, DepositService depositService) {
        this.registry = registry;
        this.depositService = depositService;
    }

    @Scheduled(fixedDelay = 15000)
    public void pollNetworks() {
        log.debug("Polling blockchain networks for new deposits...");
        
        pollNetwork("ETH", lastScannedBlockEth);
        pollNetwork("BTC", lastScannedBlockBtc);
    }

    private void pollNetwork(String networkId, long lastScannedBlock) {
        registry.getProvider(networkId).ifPresent(provider -> {
            try {
                long currentHeight = provider.getLatestBlockHeight();
                if (currentHeight > lastScannedBlock) {
                    List<DetectedDeposit> deposits = provider.scanForDeposits(lastScannedBlock + 1, currentHeight);
                    
                    deposits.forEach(deposit -> {
                        depositService.detectDeposit(new DetectDepositCommand(
                            deposit.networkId(),
                            deposit.destinationAddress(),
                            deposit.txHash(),
                            deposit.outputIndex(),
                            deposit.amount()
                        ));
                    });

                    // Update cursor (stub logic here, should update DB)
                    if (networkId.equals("ETH")) lastScannedBlockEth = currentHeight;
                    if (networkId.equals("BTC")) lastScannedBlockBtc = currentHeight;
                }
            } catch (Exception e) {
                log.error("Error polling network {}", networkId, e);
            }
        });
    }
}
