package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.application.DepositService;
import com.helium.core.wallet.application.DetectDepositCommand;
import com.helium.core.wallet.application.BlockchainConsensusStatus;
import com.helium.core.wallet.application.BlockchainCanonicalBlockService;
import com.helium.core.wallet.application.BlockchainObservationConsensus;
import com.helium.core.wallet.application.BlockchainObservationConsensusService;
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
import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "helium.wallet.chain-monitor", name = "enabled", havingValue = "true")
public class ChainMonitorJob {
    private static final Logger log = LoggerFactory.getLogger(ChainMonitorJob.class);

    private final BlockchainProviderRegistry registry;
    private final DepositService depositService;
    private final BlockchainNetworkRepository networkRepository;
    private final ChainMonitorStateRepository monitorStateRepository;
    private final DepositRepository depositRepository;
    private final WalletAuditService auditService;
    private final BlockchainProviderPool providerPool;
    private final BlockchainObservationConsensusService consensusService;
    private final BlockchainCanonicalBlockService canonicalBlockService;
    private final Clock clock;

    public ChainMonitorJob(
        BlockchainProviderRegistry registry,
        DepositService depositService,
        BlockchainNetworkRepository networkRepository,
        ChainMonitorStateRepository monitorStateRepository,
        DepositRepository depositRepository,
        WalletAuditService auditService,
        BlockchainProviderPool providerPool,
        BlockchainObservationConsensusService consensusService,
        BlockchainCanonicalBlockService canonicalBlockService,
        Clock clock
    ) {
        this.registry = registry;
        this.depositService = depositService;
        this.networkRepository = networkRepository;
        this.monitorStateRepository = monitorStateRepository;
        this.depositRepository = depositRepository;
        this.auditService = auditService;
        this.providerPool = providerPool;
        this.consensusService = consensusService;
        this.canonicalBlockService = canonicalBlockService;
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

            if (currentHeight < state.lastObservedBlockHeight()) {
                if (currentHeight < state.reorgCheckpointBlockHeight()) {
                    state.requireDeepReorgReview(clock.instant());
                    auditService.record(
                        WalletAuditEventType.CHAIN_MONITOR_UPDATED,
                        null,
                        "chain-monitor",
                        networkCode + ":deep-reorg-review-required:" + currentHeight
                    );
                }
                log.warn("Network {} reported lower height {} than previously observed {}; waiting for provider consistency",
                    networkCode, currentHeight, state.lastObservedBlockHeight());
                return;
            }

            long lastScannedBlock = state.scanCheckpointBlockHeight();
            if (currentHeight > lastScannedBlock) {
                boolean reorgDetected = observeCanonicalBlocks(provider, state, lastScannedBlock + 1, currentHeight);
                if (reorgDetected) {
                    return;
                }
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
            state.recordSuccessfulScan(
                currentHeight,
                providerPool.primary(networkCode).id(),
                null,
                null,
                clock.instant()
            );
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

    private boolean observeCanonicalBlocks(BlockchainProvider provider, ChainMonitorState state, long startBlock, long endBlock) {
        for (long height = startBlock; height <= endBlock; height++) {
            BlockchainCanonicalBlockService.CanonicalBlockObservationResult result =
                canonicalBlockService.observe(provider.getCanonicalBlock(height));
            if (result.reorgSuspected()) {
                state.requireDeepReorgReview(clock.instant());
                auditService.record(
                    WalletAuditEventType.DEEP_REORG_DETECTED,
                    null,
                    "chain-monitor",
                    provider.networkId() + ":" + height + ":" + result.previousBlockHash() + "->" + result.currentBlockHash()
                );
                return true;
            }
        }
        return false;
    }

    private void updateDepositConfirmations(BlockchainProvider provider, Deposit deposit) {
        BlockchainObservationConsensus consensus = consensusService.establish(
            deposit.networkCode(),
            deposit.txHash(),
            "DEPOSIT_CONFIRMING"
        );
        if (!consensus.agreed()) {
            if (consensus.status() == BlockchainConsensusStatus.TRANSACTION_NOT_FOUND && deposit.confirmations() > 0) {
                depositService.updateConfirmations(new UpdateDepositConfirmationsCommand(deposit.id(), -1));
            } else if (consensus.status() == BlockchainConsensusStatus.PROVIDER_DISAGREEMENT
                || consensus.status() == BlockchainConsensusStatus.INSUFFICIENT_PROVIDERS
                || consensus.status() == BlockchainConsensusStatus.REORG_SUSPECTED) {
                auditService.record(
                    WalletAuditEventType.DEPOSIT_CHAIN_REVIEW_REQUIRED,
                    deposit.id(),
                    "chain-monitor",
                    "blockchain consensus failed: " + consensus.status()
                );
            }
            return;
        }
        long confirmations = consensus.confirmations();
        int bounded = confirmations > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) confirmations;
        depositService.updateConfirmations(new UpdateDepositConfirmationsCommand(deposit.id(), bounded));
    }
}
