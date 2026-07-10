package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainBroadcast;
import com.helium.core.wallet.domain.BlockchainBroadcastStatus;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalConfirmation;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.domain.WithdrawalReorgEvent;
import com.helium.core.wallet.domain.WithdrawalStatus;
import com.helium.core.wallet.infrastructure.BlockchainBroadcastRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.WithdrawalConfirmationRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import com.helium.core.wallet.infrastructure.WithdrawalReorgEventRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import com.helium.core.wallet.infrastructure.blockchain.BlockchainProvider;
import com.helium.core.wallet.infrastructure.blockchain.BlockchainProviderRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmationMonitorService {
    static final String CONFIRMATION_ACTOR = "system:withdrawal-confirmation-monitor";

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalQueueRepository queueRepository;
    private final BlockchainBroadcastRepository broadcastRepository;
    private final WithdrawalConfirmationRepository confirmationRepository;
    private final WithdrawalReorgEventRepository reorgEventRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final BlockchainProviderRegistry providerRegistry;
    private final WithdrawalApprovalService approvalService;
    private final WithdrawalQueueService queueService;
    private final WalletAuditService auditService;
    private final Clock clock;
    private final Timer confirmationLatency;
    private final Counter confirmationFailures;
    private final Counter reorgCount;

    public ConfirmationMonitorService(
        WithdrawalRepository withdrawalRepository,
        WithdrawalQueueRepository queueRepository,
        BlockchainBroadcastRepository broadcastRepository,
        WithdrawalConfirmationRepository confirmationRepository,
        WithdrawalReorgEventRepository reorgEventRepository,
        BlockchainNetworkRepository networkRepository,
        BlockchainProviderRegistry providerRegistry,
        WithdrawalApprovalService approvalService,
        WithdrawalQueueService queueService,
        WalletAuditService auditService,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.queueRepository = queueRepository;
        this.broadcastRepository = broadcastRepository;
        this.confirmationRepository = confirmationRepository;
        this.reorgEventRepository = reorgEventRepository;
        this.networkRepository = networkRepository;
        this.providerRegistry = providerRegistry;
        this.approvalService = approvalService;
        this.queueService = queueService;
        this.auditService = auditService;
        this.clock = clock;
        this.confirmationLatency = Timer.builder("helium_wallet_confirmation_latency_seconds").register(meterRegistry);
        this.confirmationFailures = Counter.builder("helium_wallet_confirmation_failures_total").register(meterRegistry);
        this.reorgCount = Counter.builder("helium_wallet_reorgs_total").register(meterRegistry);
    }

    @Transactional
    public void check(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal was not found"));
        WithdrawalQueueItem queueItem = queueRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        if (queueItem.status() == WithdrawalQueueStatus.CONFIRMATION_FAILED) {
            queueService.beginConfirming(withdrawal, CONFIRMATION_ACTOR);
        } else if (queueItem.status() == WithdrawalQueueStatus.BROADCASTED) {
            queueService.beginConfirming(withdrawal, CONFIRMATION_ACTOR);
        } else if (queueItem.status() != WithdrawalQueueStatus.CONFIRMING
            && queueItem.status() != WithdrawalQueueStatus.BROADCASTED) {
            throw new WalletValidationException("withdrawal queue is not ready for confirmation monitoring");
        }
        BlockchainBroadcast broadcast = broadcastRepository
            .findFirstByWithdrawalIdAndStatusOrderByCreatedAtDesc(withdrawal.id(), BlockchainBroadcastStatus.BROADCASTED)
            .orElseThrow(() -> new WalletValidationException("broadcasted transaction was not found"));
        BlockchainNetwork network = networkRepository.findById(withdrawal.networkCode())
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        BlockchainProvider provider = providerRegistry.getRequiredProvider(withdrawal.networkCode());

        try {
            long observed = confirmationLatency.record(() -> provider.getConfirmations(broadcast.txHash()));
            upsertConfirmation(withdrawal, broadcast, network, observed);
        } catch (RuntimeException exception) {
            confirmationFailures.increment();
            String reason = conciseReason(exception);
            confirmationRepository.findByWithdrawalId(withdrawal.id()).ifPresent(confirmation ->
                confirmation.markFailed(reason, clock.instant()));
            auditService.record(WalletAuditEventType.WITHDRAWAL_CONFIRMATION_FAILED, withdrawal.id(), CONFIRMATION_ACTOR, reason);
            queueService.markConfirmationFailed(withdrawal, CONFIRMATION_ACTOR, reason);
        }
    }

    private void upsertConfirmation(Withdrawal withdrawal, BlockchainBroadcast broadcast, BlockchainNetwork network, long observed) {
        WithdrawalConfirmation confirmation = confirmationRepository.findByWithdrawalId(withdrawal.id())
            .orElseGet(() -> confirmationRepository.save(WithdrawalConfirmation.start(
                withdrawal.id(),
                broadcast.txHash(),
                Math.max(0, (int) observed),
                network.requiredConfirmations(),
                clock.instant()
            )));
        int previous = confirmation.confirmations();
        if (observed < 0 || observed < previous) {
            recordReorg(withdrawal, confirmation, broadcast.txHash(), previous, Math.max(0, (int) observed));
            return;
        }
        boolean changed = confirmation.update((int) observed, network.requiredConfirmations(), clock.instant());
        if (changed) {
            auditService.record(
                WalletAuditEventType.WITHDRAWAL_CONFIRMATIONS_UPDATED,
                withdrawal.id(),
                CONFIRMATION_ACTOR,
                confirmation.confirmations() + "/" + confirmation.requiredConfirmations()
            );
        }
        approvalService.observeWithdrawal(new ObserveWithdrawalCommand(
            withdrawal.id(),
            broadcast.txHash(),
            withdrawal.amount(),
            withdrawal.destinationAddress(),
            withdrawal.destinationMemo(),
            confirmation.confirmations()
        ));
        if (confirmation.confirmations() >= network.requiredConfirmations()
            && withdrawal.status() == WithdrawalStatus.BROADCASTED) {
            approvalService.confirm(new ConfirmWithdrawalCommand(withdrawal.id()));
            queueService.markConfirmed(withdrawal, CONFIRMATION_ACTOR);
        }
    }

    private void recordReorg(
        Withdrawal withdrawal,
        WithdrawalConfirmation confirmation,
        String txHash,
        int previousConfirmations,
        int currentConfirmations
    ) {
        reorgCount.increment();
        boolean manualReviewRequired = withdrawal.status() == WithdrawalStatus.CONFIRMED;
        String reason = currentConfirmations == 0 ? "transaction disappeared or lost all confirmations" : "confirmations decreased";
        confirmation.markReorgDetected(reason, clock.instant());
        reorgEventRepository.save(WithdrawalReorgEvent.detected(
            withdrawal.id(),
            withdrawal.assetCode(),
            withdrawal.networkCode(),
            txHash,
            previousConfirmations,
            currentConfirmations,
            reason,
            manualReviewRequired,
            clock.instant()
        ));
        auditService.record(WalletAuditEventType.WITHDRAWAL_REORG_DETECTED, withdrawal.id(), CONFIRMATION_ACTOR, reason);
        if (!manualReviewRequired) {
            queueService.markReorgDetected(withdrawal, CONFIRMATION_ACTOR, reason);
        }
    }

    private static String conciseReason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message.substring(0, Math.min(500, message.length()));
    }
}
