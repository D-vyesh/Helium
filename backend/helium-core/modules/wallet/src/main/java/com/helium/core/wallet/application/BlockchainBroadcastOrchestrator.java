package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainBroadcast;
import com.helium.core.wallet.domain.BlockchainBroadcastStatus;
import com.helium.core.wallet.domain.SignedTransaction;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.domain.WithdrawalStatus;
import com.helium.core.wallet.infrastructure.BlockchainBroadcastRepository;
import com.helium.core.wallet.infrastructure.SignedTransactionRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockchainBroadcastOrchestrator {
    static final String BROADCASTER_ACTOR = "system:blockchain-broadcaster";

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalQueueRepository queueRepository;
    private final SignedTransactionRepository signedTransactionRepository;
    private final BlockchainBroadcastRepository broadcastRepository;
    private final BlockchainBroadcasterRouter broadcasterRouter;
    private final WithdrawalQueueService queueService;
    private final WalletAuditService auditService;
    private final Clock clock;
    private final int maxRetries;
    private final Timer broadcastLatency;
    private final Counter broadcastFailures;
    private final Counter rpcFailures;

    public BlockchainBroadcastOrchestrator(
        WithdrawalRepository withdrawalRepository,
        WithdrawalQueueRepository queueRepository,
        SignedTransactionRepository signedTransactionRepository,
        BlockchainBroadcastRepository broadcastRepository,
        BlockchainBroadcasterRouter broadcasterRouter,
        WithdrawalQueueService queueService,
        WalletAuditService auditService,
        Clock clock,
        MeterRegistry meterRegistry,
        @Value("${helium.wallet.broadcast.max-retries:5}") int maxRetries
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.queueRepository = queueRepository;
        this.signedTransactionRepository = signedTransactionRepository;
        this.broadcastRepository = broadcastRepository;
        this.broadcasterRouter = broadcasterRouter;
        this.queueService = queueService;
        this.auditService = auditService;
        this.clock = clock;
        this.maxRetries = Math.max(1, maxRetries);
        this.broadcastLatency = Timer.builder("helium_wallet_broadcast_latency_seconds").register(meterRegistry);
        this.broadcastFailures = Counter.builder("helium_wallet_broadcast_failures_total").register(meterRegistry);
        this.rpcFailures = Counter.builder("helium_wallet_rpc_failures_total").tag("stage", "broadcast").register(meterRegistry);
    }

    @Transactional
    public void broadcast(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal was not found"));
        WithdrawalQueueItem queueItem = queueRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        validateReady(withdrawal, queueItem);
        SignedTransaction signedTransaction = signedTransactionRepository.findByWithdrawalId(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("signed transaction was not found"));

        var existingBroadcast = broadcastRepository
            .findFirstByWithdrawalIdAndStatusOrderByCreatedAtDesc(withdrawalId, BlockchainBroadcastStatus.BROADCASTED);
        if (existingBroadcast.isPresent()) {
            markWithdrawalBroadcastedIfRequired(withdrawal, existingBroadcast.get().txHash());
            if (queueItem.status() == WithdrawalQueueStatus.WAITING_BROADCAST
                || queueItem.status() == WithdrawalQueueStatus.BROADCAST_FAILED) {
                queueService.beginBroadcast(withdrawal, BROADCASTER_ACTOR);
                queueService.markBroadcasted(withdrawal, BROADCASTER_ACTOR);
            }
            return;
        }

        int attemptNumber = broadcastRepository.countByWithdrawalId(withdrawalId) + 1;
        queueService.beginBroadcast(withdrawal, BROADCASTER_ACTOR);
        try {
            BroadcastResult result = broadcastLatency.record(() ->
                broadcasterRouter.requiredBroadcaster(withdrawal.assetCode()).broadcast(signedTransaction));
            broadcastRepository.save(BlockchainBroadcast.broadcasted(
                withdrawal.id(),
                signedTransaction.id(),
                withdrawal.assetCode(),
                withdrawal.networkCode(),
                attemptNumber,
                result.txHash(),
                result.provider(),
                result.nodeId(),
                result.feePaid(),
                result.rpcLatency().toMillis(),
                result.rawResponse(),
                result.broadcastedAt()
            ));
            markWithdrawalBroadcastedIfRequired(withdrawal, result.txHash());
            auditService.record(WalletAuditEventType.WITHDRAWAL_BROADCAST_SUBMITTED, withdrawal.id(), BROADCASTER_ACTOR, result.txHash());
            queueService.markBroadcasted(withdrawal, BROADCASTER_ACTOR);
        } catch (RuntimeException exception) {
            broadcastFailures.increment();
            rpcFailures.increment();
            String reason = conciseReason(exception);
            broadcastRepository.save(BlockchainBroadcast.failed(
                withdrawal.id(),
                signedTransaction.id(),
                withdrawal.assetCode(),
                withdrawal.networkCode(),
                attemptNumber,
                "RPC",
                "configured-provider",
                null,
                reason,
                clock.instant()
            ));
            auditService.record(WalletAuditEventType.WITHDRAWAL_BROADCAST_FAILED, withdrawal.id(), BROADCASTER_ACTOR, reason);
            queueService.markBroadcastFailed(withdrawal, BROADCASTER_ACTOR, reason);
            if (isNonRetryable(reason) || queueItem.broadcastAttempts() + 1 >= maxRetries) {
                queueService.fail(withdrawal, BROADCASTER_ACTOR, "broadcast failed: " + reason);
            }
        }
    }

    private void validateReady(Withdrawal withdrawal, WithdrawalQueueItem queueItem) {
        if (withdrawal.status() != WithdrawalStatus.APPROVED) {
            throw new WalletValidationException("withdrawal must be approved before blockchain broadcasting");
        }
        if (queueItem.status() != WithdrawalQueueStatus.WAITING_BROADCAST
            && queueItem.status() != WithdrawalQueueStatus.BROADCAST_FAILED) {
            throw new WalletValidationException("withdrawal queue is not ready for blockchain broadcasting");
        }
    }

    private void markWithdrawalBroadcastedIfRequired(Withdrawal withdrawal, String txHash) {
        if (withdrawal.status() == WithdrawalStatus.APPROVED) {
            withdrawal.recordBroadcast(txHash, clock.instant());
        }
    }

    private static boolean isNonRetryable(String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("invalid signature")
            || lower.contains("mandatory-script-verify-flag-failed")
            || lower.contains("malformed")
            || lower.contains("insufficient funds")
            || lower.contains("nonce too low");
    }

    private static String conciseReason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message.substring(0, Math.min(500, message.length()));
    }
}
