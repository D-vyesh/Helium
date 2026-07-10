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
@ConditionalOnProperty(name = "helium.wallet.confirmation.worker.enabled", havingValue = "true")
public class ConfirmationMonitorWorker {
    private static final Logger log = LoggerFactory.getLogger(ConfirmationMonitorWorker.class);

    private final WithdrawalQueueRepository queueRepository;
    private final ConfirmationMonitorService confirmationMonitorService;
    private final Clock clock;

    public ConfirmationMonitorWorker(
        WithdrawalQueueRepository queueRepository,
        ConfirmationMonitorService confirmationMonitorService,
        Clock clock
    ) {
        this.queueRepository = queueRepository;
        this.confirmationMonitorService = confirmationMonitorService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${helium.wallet.confirmation.worker.poll-interval-ms:15000}")
    public void monitorConfirmations() {
        for (WithdrawalQueueItem item : queueRepository.findTop50ByStatusInOrderByUpdatedAtAsc(List.of(
            WithdrawalQueueStatus.BROADCASTED,
            WithdrawalQueueStatus.CONFIRMING,
            WithdrawalQueueStatus.CONFIRMATION_FAILED
        ))) {
            if (item.status() == WithdrawalQueueStatus.CONFIRMATION_FAILED
                && item.nextConfirmationAttemptAt() != null
                && item.nextConfirmationAttemptAt().isAfter(clock.instant())) {
                continue;
            }
            try {
                confirmationMonitorService.check(item.withdrawalId());
            } catch (RuntimeException exception) {
                log.warn("Confirmation monitor failed for withdrawal {}", item.withdrawalId(), exception);
            }
        }
    }
}
