package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.domain.WithdrawalStatus;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.UnsignedTransactionRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds and persists unsigned drafts only; custody signing and network submission remain separate stages. */
@Service
public class UnsignedTransactionBuildService {
    static final String BUILDER_ACTOR = "system:transaction-builder";
    private static final Logger log = LoggerFactory.getLogger(UnsignedTransactionBuildService.class);

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalQueueRepository queueRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final UnsignedTransactionRepository unsignedTransactionRepository;
    private final WithdrawalQueueService queueService;
    private final UnsignedTransactionBuilderRouter router;
    private final WithdrawalAddressValidator addressValidator;
    private final Clock clock;
    private final Timer buildTimer;
    private final Counter buildFailures;
    private final Counter buildRetries;

    public UnsignedTransactionBuildService(
        WithdrawalRepository withdrawalRepository,
        WithdrawalQueueRepository queueRepository,
        BlockchainNetworkRepository networkRepository,
        UnsignedTransactionRepository unsignedTransactionRepository,
        WithdrawalQueueService queueService,
        UnsignedTransactionBuilderRouter router,
        WithdrawalAddressValidator addressValidator,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.queueRepository = queueRepository;
        this.networkRepository = networkRepository;
        this.unsignedTransactionRepository = unsignedTransactionRepository;
        this.queueService = queueService;
        this.router = router;
        this.addressValidator = addressValidator;
        this.clock = clock;
        this.buildTimer = Timer.builder("helium_wallet_transaction_builder_duration_seconds").register(meterRegistry);
        this.buildFailures = Counter.builder("helium_wallet_transaction_builder_failures_total").register(meterRegistry);
        this.buildRetries = Counter.builder("helium_wallet_transaction_builder_retries_total").register(meterRegistry);
    }

    @Transactional
    public void build(UUID withdrawalId, FeeTier feeTier) {
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal was not found"));
        WithdrawalQueueItem queueItem = queueRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        if (queueItem.status() == WithdrawalQueueStatus.BUILD_FAILED) {
            buildRetries.increment();
        }
        validateBuildRequest(withdrawal, queueItem);
        queueService.beginTransactionBuild(withdrawal, BUILDER_ACTOR);
        try {
            UnsignedTransaction existing = unsignedTransactionRepository.findByWithdrawalId(withdrawalId).orElse(null);
            if (existing == null) {
                UnsignedTransactionDraft draft = buildTimer.record(() -> router.requiredBuilder(withdrawal.assetCode()).build(withdrawal, feeTier));
                if (withdrawal.fee().compareTo(draft.fee()) < 0) {
                    throw new WalletValidationException("authorized withdrawal fee is insufficient for the live network fee estimate");
                }
                unsignedTransactionRepository.save(UnsignedTransaction.built(
                    withdrawal.id(), withdrawal.assetCode(), withdrawal.networkCode(), draft.format(), draft.builderVersion(),
                    draft.serializedPayload(), draft.psbt(), draft.nonce(), draft.recentBlockhash(), draft.fee(), draft.metadata(), clock.instant()
                ));
            }
            queueService.markTransactionBuilt(withdrawal, BUILDER_ACTOR);
        } catch (RuntimeException exception) {
            buildFailures.increment();
            queueService.markBuildFailed(withdrawal, BUILDER_ACTOR, conciseReason(exception));
            log.warn("Unsigned transaction build failed for withdrawal {}", withdrawal.id(), exception);
        }
    }

    private void validateBuildRequest(Withdrawal withdrawal, WithdrawalQueueItem queueItem) {
        if (withdrawal.status() != WithdrawalStatus.APPROVED) {
            throw new WalletValidationException("withdrawal must be approved before building a transaction");
        }
        if (queueItem.status() != WithdrawalQueueStatus.WAITING_SIGN && queueItem.status() != WithdrawalQueueStatus.BUILD_FAILED) {
            throw new WalletValidationException("withdrawal queue is not ready to build a transaction");
        }
        BlockchainNetwork network = networkRepository.findById(withdrawal.networkCode())
            .orElseThrow(() -> new WalletValidationException("withdrawal network was not found"));
        if (!network.withdrawalEnabled() || !withdrawal.assetCode().equals(network.assetCode())) {
            throw new WalletValidationException("withdrawal network is not enabled for this asset");
        }
        addressValidator.validate(withdrawal.assetCode(), withdrawal.networkCode(), withdrawal.destinationAddress());
    }

    private static String conciseReason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0, Math.min(500, message.length()));
    }
}
