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
@ConditionalOnProperty(name = "helium.wallet.custody.signing.worker.enabled", havingValue = "true")
public class CustodySigningWorker {
    private static final Logger log = LoggerFactory.getLogger(CustodySigningWorker.class);

    private final WithdrawalQueueRepository queueRepository;
    private final CustodySigningService signingService;
    private final Clock clock;

    public CustodySigningWorker(
        WithdrawalQueueRepository queueRepository,
        CustodySigningService signingService,
        Clock clock
    ) {
        this.queueRepository = queueRepository;
        this.signingService = signingService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${helium.wallet.custody.signing.worker.poll-interval-ms:5000}")
    public void signReadyWithdrawals() {
        for (WithdrawalQueueItem item : queueRepository.findTop50ByStatusInOrderByUpdatedAtAsc(List.of(
            WithdrawalQueueStatus.WAITING_SIGNER,
            WithdrawalQueueStatus.SIGN_FAILED
        ))) {
            if (item.status() == WithdrawalQueueStatus.SIGN_FAILED
                && item.nextSignAttemptAt() != null
                && item.nextSignAttemptAt().isAfter(clock.instant())) {
                continue;
            }
            try {
                signingService.sign(item.withdrawalId());
            } catch (RuntimeException exception) {
                log.warn("Custody signing failed for withdrawal {}", item.withdrawalId(), exception);
            }
        }
    }
}
