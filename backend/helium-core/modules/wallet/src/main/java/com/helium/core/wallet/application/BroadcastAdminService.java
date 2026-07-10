package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainBroadcast;
import com.helium.core.wallet.domain.BlockchainBroadcastStatus;
import com.helium.core.wallet.domain.WithdrawalConfirmation;
import com.helium.core.wallet.domain.WithdrawalConfirmationStatus;
import com.helium.core.wallet.domain.WithdrawalReorgEvent;
import com.helium.core.wallet.infrastructure.BlockchainBroadcastRepository;
import com.helium.core.wallet.infrastructure.WithdrawalConfirmationRepository;
import com.helium.core.wallet.infrastructure.WithdrawalReorgEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BroadcastAdminService {
    private final BlockchainBroadcastRepository broadcastRepository;
    private final WithdrawalConfirmationRepository confirmationRepository;
    private final WithdrawalReorgEventRepository reorgEventRepository;
    private final Clock clock;
    private final Duration stuckThreshold;

    public BroadcastAdminService(
        BlockchainBroadcastRepository broadcastRepository,
        WithdrawalConfirmationRepository confirmationRepository,
        WithdrawalReorgEventRepository reorgEventRepository,
        Clock clock,
        @Value("${helium.wallet.stuck-monitor.threshold-minutes:60}") long thresholdMinutes
    ) {
        this.broadcastRepository = broadcastRepository;
        this.confirmationRepository = confirmationRepository;
        this.reorgEventRepository = reorgEventRepository;
        this.clock = clock;
        this.stuckThreshold = Duration.ofMinutes(Math.max(1, thresholdMinutes));
    }

    public List<BroadcastAdminView> broadcasts() {
        return broadcastRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toBroadcastView).toList();
    }

    public List<TransactionAdminView> transactions() {
        return confirmationRepository.findTop100ByOrderByLastCheckedAtDesc().stream().map(this::toTransactionView).toList();
    }

    public Optional<TransactionAdminView> transaction(UUID withdrawalId) {
        return confirmationRepository.findByWithdrawalId(withdrawalId).map(this::toTransactionView);
    }

    public List<ReorgAdminView> reorgs() {
        return reorgEventRepository.findTop100ByOrderByDetectedAtDesc().stream().map(this::toReorgView).toList();
    }

    public List<BroadcastAdminView> stuckTransactions() {
        Instant cutoff = clock.instant().minus(stuckThreshold);
        return broadcastRepository
            .findTop100ByStatusAndBroadcastedAtBeforeOrderByBroadcastedAtAsc(BlockchainBroadcastStatus.BROADCASTED, cutoff)
            .stream()
            .filter(broadcast -> confirmationRepository.findByWithdrawalId(broadcast.withdrawalId())
                .map(confirmation -> confirmation.status() != WithdrawalConfirmationStatus.CONFIRMED)
                .orElse(true))
            .map(this::toBroadcastView)
            .toList();
    }

    private BroadcastAdminView toBroadcastView(BlockchainBroadcast broadcast) {
        return new BroadcastAdminView(
            broadcast.id(),
            broadcast.withdrawalId(),
            broadcast.assetCode(),
            broadcast.networkCode(),
            broadcast.attemptNumber(),
            broadcast.txHash(),
            broadcast.status(),
            broadcast.provider(),
            broadcast.nodeId(),
            broadcast.feePaid(),
            broadcast.rpcLatencyMs(),
            broadcast.errorReason(),
            broadcast.broadcastedAt(),
            broadcast.createdAt()
        );
    }

    private TransactionAdminView toTransactionView(WithdrawalConfirmation confirmation) {
        BlockchainBroadcast broadcast = broadcastRepository
            .findFirstByWithdrawalIdAndStatusOrderByCreatedAtDesc(confirmation.withdrawalId(), BlockchainBroadcastStatus.BROADCASTED)
            .orElse(null);
        return new TransactionAdminView(
            confirmation.withdrawalId(),
            broadcast == null ? null : broadcast.assetCode(),
            broadcast == null ? null : broadcast.networkCode(),
            confirmation.txHash(),
            confirmation.confirmations(),
            confirmation.requiredConfirmations(),
            confirmation.status(),
            confirmation.lastCheckedAt(),
            confirmation.confirmedAt(),
            confirmation.failureReason()
        );
    }

    private ReorgAdminView toReorgView(WithdrawalReorgEvent event) {
        return new ReorgAdminView(
            event.id(),
            event.withdrawalId(),
            event.assetCode(),
            event.networkCode(),
            event.txHash(),
            event.previousConfirmations(),
            event.currentConfirmations(),
            event.reason(),
            event.manualReviewRequired(),
            event.detectedAt()
        );
    }
}
