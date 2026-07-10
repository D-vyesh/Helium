package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.domain.BlockchainBroadcast;
import com.helium.core.wallet.domain.BlockchainBroadcastStatus;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WithdrawalConfirmationStatus;
import com.helium.core.wallet.application.WalletAuditService;
import com.helium.core.wallet.infrastructure.BlockchainBroadcastRepository;
import com.helium.core.wallet.infrastructure.WithdrawalConfirmationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "helium.wallet.stuck-monitor.enabled", havingValue = "true")
public class StuckTransactionMonitor {
    private static final Logger log = LoggerFactory.getLogger(StuckTransactionMonitor.class);

    private final BlockchainBroadcastRepository broadcastRepository;
    private final WithdrawalConfirmationRepository confirmationRepository;
    private final WalletAuditService auditService;
    private final Clock clock;
    private final Duration threshold;

    public StuckTransactionMonitor(
        BlockchainBroadcastRepository broadcastRepository,
        WithdrawalConfirmationRepository confirmationRepository,
        WalletAuditService auditService,
        Clock clock,
        @Value("${helium.wallet.stuck-monitor.threshold-minutes:60}") long thresholdMinutes
    ) {
        this.broadcastRepository = broadcastRepository;
        this.confirmationRepository = confirmationRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.threshold = Duration.ofMinutes(Math.max(1, thresholdMinutes));
    }

    @Scheduled(fixedDelayString = "${helium.wallet.stuck-monitor.poll-interval-ms:600000}")
    public void monitorStuckTransactions() {
        Instant cutoff = clock.instant().minus(threshold);
        for (BlockchainBroadcast broadcast : broadcastRepository
            .findTop100ByStatusAndBroadcastedAtBeforeOrderByBroadcastedAtAsc(BlockchainBroadcastStatus.BROADCASTED, cutoff)) {
            boolean confirmed = confirmationRepository.findByWithdrawalId(broadcast.withdrawalId())
                .map(confirmation -> confirmation.status() == WithdrawalConfirmationStatus.CONFIRMED)
                .orElse(false);
            if (confirmed) {
                continue;
            }
            String detail = "stuck transaction candidate " + broadcast.assetCode() + ":" + broadcast.txHash();
            auditService.record(
                WalletAuditEventType.WITHDRAWAL_STUCK_TRANSACTION_DETECTED,
                broadcast.withdrawalId(),
                "system:stuck-transaction-monitor",
                detail
            );
            log.warn("{} requires operator review; no automatic replacement transaction is signed or broadcast", detail);
        }
    }
}
