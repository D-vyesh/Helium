package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.domain.WithdrawalQueueTransition;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueTransitionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalQueueService {
    private static final Set<String> NATIVE_ASSETS = Set.of("BTC", "ETH", "SOL");

    private final WithdrawalQueueRepository queueRepository;
    private final WithdrawalQueueTransitionRepository transitionRepository;
    private final WalletAuditService auditService;
    private final Clock clock;

    public WithdrawalQueueService(
        WithdrawalQueueRepository queueRepository,
        WithdrawalQueueTransitionRepository transitionRepository,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.queueRepository = queueRepository;
        this.transitionRepository = transitionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void enqueueIfRequired(Withdrawal withdrawal, String actorId) {
        if (!requiresQueue(withdrawal)) {
            return;
        }
        if (queueRepository.findByWithdrawalIdForUpdate(withdrawal.id()).isPresent()) {
            return;
        }
        WithdrawalQueueItem item = queueRepository.save(WithdrawalQueueItem.enqueue(withdrawal.id(), clock.instant()));
        record(item, actorId, "withdrawal requested");
        auditService.record(WalletAuditEventType.WITHDRAWAL_QUEUED, withdrawal.id(), actorId, WithdrawalQueueStatus.REQUESTED.name());
    }

    @Transactional
    public void beginValidation(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.VALIDATING, actorId, "customer email and MFA confirmed");
    }

    @Transactional
    public void markApproved(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.APPROVED, actorId, "maker-checker approval complete");
        transition(withdrawal, WithdrawalQueueStatus.WAITING_SIGN, actorId, "awaiting configured transaction builder and signer");
    }

    @Transactional
    public void beginTransactionBuild(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.BUILDING_TRANSACTION, actorId, "building unsigned transaction");
    }

    @Transactional
    public void markTransactionBuilt(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.TRANSACTION_BUILT, actorId, "unsigned transaction persisted");
        transition(withdrawal, WithdrawalQueueStatus.WAITING_SIGNER, actorId, "awaiting custody signer");
    }

    @Transactional
    public void beginSigning(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.SIGNING, actorId, "custody signing started");
    }

    @Transactional
    public void markSigned(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.SIGNED, actorId, "signed transaction persisted");
        transition(withdrawal, WithdrawalQueueStatus.WAITING_BROADCAST, actorId, "awaiting broadcast queue");
    }

    @Transactional
    public void beginBroadcast(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.BROADCASTING, actorId, "broadcasting signed transaction");
    }

    @Transactional
    public void markBroadcasted(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.BROADCASTED, actorId, "transaction submitted to blockchain RPC");
        transition(withdrawal, WithdrawalQueueStatus.CONFIRMING, actorId, "awaiting blockchain confirmations");
    }

    @Transactional
    public void beginConfirming(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.CONFIRMING, actorId, "checking blockchain confirmations");
    }

    @Transactional
    public void markConfirmed(Withdrawal withdrawal, String actorId) {
        transition(withdrawal, WithdrawalQueueStatus.CONFIRMED, actorId, "withdrawal reached required confirmations");
    }

    @Transactional
    public void markBuildFailed(Withdrawal withdrawal, String actorId, String reason) {
        if (!requiresQueue(withdrawal)) {
            return;
        }
        WithdrawalQueueItem item = queueRepository.findByWithdrawalIdForUpdate(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        Instant now = clock.instant();
        item.recordBuildFailure(reason, now.plus(backoff(item.buildAttempts())), now);
        record(item, actorId, reason);
        auditService.record(WalletAuditEventType.WITHDRAWAL_QUEUE_TRANSITIONED, withdrawal.id(), actorId, item.status().name());
    }

    @Transactional
    public void markSignFailed(Withdrawal withdrawal, String actorId, String reason) {
        if (!requiresQueue(withdrawal)) {
            return;
        }
        WithdrawalQueueItem item = queueRepository.findByWithdrawalIdForUpdate(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        Instant now = clock.instant();
        item.recordSignFailure(reason, now.plus(backoff(item.signAttempts())), now);
        record(item, actorId, reason);
        auditService.record(WalletAuditEventType.WITHDRAWAL_QUEUE_TRANSITIONED, withdrawal.id(), actorId, item.status().name());
    }

    @Transactional
    public void markBroadcastFailed(Withdrawal withdrawal, String actorId, String reason) {
        if (!requiresQueue(withdrawal)) {
            return;
        }
        WithdrawalQueueItem item = queueRepository.findByWithdrawalIdForUpdate(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        Instant now = clock.instant();
        item.recordBroadcastFailure(reason, now.plus(backoff(item.broadcastAttempts())), now);
        record(item, actorId, reason);
        auditService.record(WalletAuditEventType.WITHDRAWAL_QUEUE_TRANSITIONED, withdrawal.id(), actorId, item.status().name());
    }

    @Transactional
    public void markConfirmationFailed(Withdrawal withdrawal, String actorId, String reason) {
        if (!requiresQueue(withdrawal)) {
            return;
        }
        WithdrawalQueueItem item = queueRepository.findByWithdrawalIdForUpdate(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        Instant now = clock.instant();
        item.recordConfirmationFailure(reason, now.plus(backoff(item.confirmationFailures())), now);
        record(item, actorId, reason);
        auditService.record(WalletAuditEventType.WITHDRAWAL_QUEUE_TRANSITIONED, withdrawal.id(), actorId, item.status().name());
    }

    @Transactional
    public void markReorgDetected(Withdrawal withdrawal, String actorId, String reason) {
        transition(withdrawal, WithdrawalQueueStatus.REORG_DETECTED, actorId, reason);
    }

    @Transactional
    public void fail(Withdrawal withdrawal, String actorId, String reason) {
        transition(withdrawal, WithdrawalQueueStatus.FAILED, actorId, reason);
    }

    private void transition(Withdrawal withdrawal, WithdrawalQueueStatus nextStatus, String actorId, String reason) {
        if (!requiresQueue(withdrawal)) {
            return;
        }
        WithdrawalQueueItem item = queueRepository.findByWithdrawalIdForUpdate(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        WithdrawalQueueStatus before = item.status();
        item.transitionTo(nextStatus, reason, clock.instant());
        if (before != item.status()) {
            record(item, actorId, reason);
            auditService.record(WalletAuditEventType.WITHDRAWAL_QUEUE_TRANSITIONED, withdrawal.id(), actorId, item.status().name());
        }
    }

    private void record(WithdrawalQueueItem item, String actorId, String reason) {
        transitionRepository.save(WithdrawalQueueTransition.record(item.id(), item.status(), actorId, reason, clock.instant()));
    }

    private static boolean requiresQueue(Withdrawal withdrawal) {
        return NATIVE_ASSETS.contains(withdrawal.assetCode());
    }

    private static Duration backoff(int priorAttempts) {
        long seconds = Math.min(300L, 5L * (1L << Math.min(priorAttempts, 6)));
        return Duration.ofSeconds(seconds);
    }
}
