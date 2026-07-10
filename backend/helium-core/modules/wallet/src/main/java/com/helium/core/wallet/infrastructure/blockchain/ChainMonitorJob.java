package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.application.DepositService;
import com.helium.core.wallet.application.DetectDepositCommand;
import com.helium.core.wallet.application.UpdateDepositConfirmationsCommand;
import com.helium.core.wallet.application.WalletAuditService;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.ChainMonitorState;
import com.helium.core.wallet.domain.Deposit;
import com.helium.core.wallet.domain.DepositStatus;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.ChainMonitorStateRepository;
import com.helium.core.wallet.infrastructure.DepositRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChainMonitorJob {
    private static final Logger log = LoggerFactory.getLogger(ChainMonitorJob.class);

    private final BlockchainProviderRegistry registry;
    private final DepositService depositService;
    private final BlockchainNetworkRepository networkRepository;
    private final ChainMonitorStateRepository monitorStateRepository;
    private final DepositRepository depositRepository;
    private final WalletAuditService auditService;
    private final Clock clock;

    public ChainMonitorJob(
        BlockchainProviderRegistry registry,
        DepositService depositService,
        BlockchainNetworkRepository networkRepository,
        ChainMonitorStateRepository monitorStateRepository,
        DepositRepository depositRepository,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.registry = registry;
        this.depositService = depositService;
        this.networkRepository = networkRepository;
        this.monitorStateRepository = monitorStateRepository;
        this.depositRepository = depositRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void pollNetworks() {
        log.debug("Polling blockchain networks for new deposits...");
        networkRepository.findAll().stream()
            .filter(BlockchainNetwork::depositEnabled)
            .filter(network -> registry.getProvider(network.networkCode()).isPresent())
            .forEach(this::pollNetwork);
    }

    void pollNetwork(BlockchainNetwork network) {
        String networkCode = network.networkCode();
        try {
            BlockchainProvider provider = registry.getRequiredProvider(networkCode);
            long currentHeight = provider.getLatestBlockHeight();
            ChainMonitorState state = monitorStateRepository.findByNetworkCodeForUpdate(networkCode)
                .orElseGet(() -> monitorStateRepository.save(ChainMonitorState.start(
                    networkCode,
                    currentHeight,
                    clock.instant()
                )));

            long lastScannedBlock = state.lastObservedBlockHeight();
            if (currentHeight > lastScannedBlock) {
                List<DetectedDeposit> deposits = provider.scanForDeposits(lastScannedBlock + 1, currentHeight);
                deposits.forEach(deposit -> depositService.detectDeposit(new DetectDepositCommand(
                    deposit.networkId(),
                    deposit.destinationAddress(),
                    deposit.txHash(),
                    deposit.outputIndex(),
                    deposit.amount()
                )));
            }

            updatePendingConfirmations(networkCode, provider);

            long confirmedHeight = Math.max(0, currentHeight - network.requiredConfirmations() + 1);
            long reorgCheckpointHeight = Math.max(0, currentHeight - (network.requiredConfirmations() * 2L));
            state.advanceTo(currentHeight, confirmedHeight, reorgCheckpointHeight, clock.instant());
            auditService.record(
                WalletAuditEventType.CHAIN_MONITOR_UPDATED,
                null,
                "chain-monitor",
                networkCode + ":" + currentHeight
            );
        } catch (Exception exception) {
            log.error("Error polling network {}", networkCode, exception);
        }
    }

    private void updatePendingConfirmations(String networkCode, BlockchainProvider provider) {
        depositRepository.findAllByNetworkCodeAndStatusIn(networkCode, List.of(
            DepositStatus.DETECTED,
            DepositStatus.PENDING_CONFIRMATIONS,
            DepositStatus.CONFIRMED,
            DepositStatus.POSTED_TO_LEDGER
        ))
            .forEach(deposit -> updateDepositConfirmations(provider, deposit));
    }

    private void updateDepositConfirmations(BlockchainProvider provider, Deposit deposit) {
        long confirmations = provider.getConfirmations(deposit.txHash());
        int bounded = confirmations > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) confirmations;
        depositService.updateConfirmations(new UpdateDepositConfirmationsCommand(deposit.id(), bounded));
    }
}
