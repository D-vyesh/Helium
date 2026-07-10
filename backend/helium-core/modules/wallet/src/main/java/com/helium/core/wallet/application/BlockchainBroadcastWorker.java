package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "helium.wallet.broadcast.worker.enabled", havingValue = "true")
public class BlockchainBroadcastWorker {
    private static final Logger log = LoggerFactory.getLogger(BlockchainBroadcastWorker.class);

    private final WithdrawalQueueRepository queueRepository;
    private final BlockchainBroadcastOrchestrator broadcastOrchestrator;
    private final Clock clock;

    public BlockchainBroadcastWorker(
        WithdrawalQueueRepository queueRepository,
        BlockchainBroadcastOrchestrator broadcastOrchestrator,
        Clock clock
    ) {
        this.queueRepository = queueRepository;
        this.broadcastOrchestrator = broadcastOrchestrator;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${helium.wallet.broadcast.worker.poll-interval-ms:5000}")
    public void broadcastReadyWithdrawals() {
        for (WithdrawalQueueItem item : queueRepository.findTop50ByStatusInOrderByUpdatedAtAsc(List.of(
            WithdrawalQueueStatus.WAITING_BROADCAST,
            WithdrawalQueueStatus.BROADCAST_FAILED
        ))) {
            if (item.status() == WithdrawalQueueStatus.BROADCAST_FAILED
                && item.nextBroadcastAttemptAt() != null
                && item.nextBroadcastAttemptAt().isAfter(clock.instant())) {
                continue;
            }
            try {
                broadcastOrchestrator.broadcast(item.withdrawalId());
            } catch (RuntimeException exception) {
                log.warn("Blockchain broadcast failed for withdrawal {}", item.withdrawalId(), exception);
            }
        }
    }
}
