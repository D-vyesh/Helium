package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in durable worker. It builds drafts only and never invokes custody or a blockchain broadcaster. */
@Component
@ConditionalOnProperty(name = "helium.wallet.builder.worker.enabled", havingValue = "true")
public class UnsignedTransactionBuildWorker {
    private static final Logger log = LoggerFactory.getLogger(UnsignedTransactionBuildWorker.class);
    private final WithdrawalQueueRepository queueRepository;
    private final UnsignedTransactionBuildService buildService;
    private final Clock clock;

    public UnsignedTransactionBuildWorker(
        WithdrawalQueueRepository queueRepository,
        UnsignedTransactionBuildService buildService,
        Clock clock
    ) {
        this.queueRepository = queueRepository;
        this.buildService = buildService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${helium.wallet.builder.worker.poll-interval-ms:5000}")
    public void buildReadyWithdrawals() {
        process(WithdrawalQueueStatus.WAITING_SIGN, false);
        process(WithdrawalQueueStatus.BUILD_FAILED, true);
    }

    private void process(WithdrawalQueueStatus status, boolean honorBackoff) {
        for (WithdrawalQueueItem item : queueRepository.findTop50ByStatusOrderByUpdatedAtAsc(status)) {
            if (honorBackoff && item.nextBuildAttemptAt() != null && item.nextBuildAttemptAt().isAfter(clock.instant())) {
                continue;
            }
            try {
                buildService.build(item.withdrawalId(), FeeTier.MEDIUM);
            } catch (RuntimeException exception) {
                // The service persists the failure and retry schedule before the worker returns.
                log.warn("Unsigned transaction build failed for withdrawal {}", item.withdrawalId(), exception);
            }
        }
    }
}
