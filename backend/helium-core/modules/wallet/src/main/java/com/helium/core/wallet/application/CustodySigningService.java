package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.CustodyKeyStatus;
import com.helium.core.wallet.domain.CustodySigningAuditEvent;
import com.helium.core.wallet.domain.SignedTransaction;
import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import com.helium.core.wallet.domain.WithdrawalStatus;
import com.helium.core.wallet.infrastructure.CustodyKeyRepository;
import com.helium.core.wallet.infrastructure.CustodySigningAuditEventRepository;
import com.helium.core.wallet.infrastructure.SignedTransactionRepository;
import com.helium.core.wallet.infrastructure.UnsignedTransactionRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustodySigningService {
    static final String SIGNER_ACTOR = "system:custody-signer";

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalQueueRepository queueRepository;
    private final UnsignedTransactionRepository unsignedTransactionRepository;
    private final SignedTransactionRepository signedTransactionRepository;
    private final CustodyKeyRepository custodyKeyRepository;
    private final CustodySigningAuditEventRepository signingAuditRepository;
    private final WithdrawalQueueService queueService;
    private final WalletAuditService walletAuditService;
    private final CustodyProviderRouter providerRouter;
    private final TransactionSignatureAssemblerRouter assemblerRouter;
    private final Clock clock;
    private final int maxRetries;
    private final Timer signingLatency;
    private final Counter signingRequests;
    private final Counter signingSuccess;
    private final Counter signingFailure;
    private final Counter retryCount;

    public CustodySigningService(
        WithdrawalRepository withdrawalRepository,
        WithdrawalQueueRepository queueRepository,
        UnsignedTransactionRepository unsignedTransactionRepository,
        SignedTransactionRepository signedTransactionRepository,
        CustodyKeyRepository custodyKeyRepository,
        CustodySigningAuditEventRepository signingAuditRepository,
        WithdrawalQueueService queueService,
        WalletAuditService walletAuditService,
        CustodyProviderRouter providerRouter,
        TransactionSignatureAssemblerRouter assemblerRouter,
        Clock clock,
        MeterRegistry meterRegistry,
        @Value("${helium.wallet.custody.signing.max-retries:5}") int maxRetries
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.queueRepository = queueRepository;
        this.unsignedTransactionRepository = unsignedTransactionRepository;
        this.signedTransactionRepository = signedTransactionRepository;
        this.custodyKeyRepository = custodyKeyRepository;
        this.signingAuditRepository = signingAuditRepository;
        this.queueService = queueService;
        this.walletAuditService = walletAuditService;
        this.providerRouter = providerRouter;
        this.assemblerRouter = assemblerRouter;
        this.clock = clock;
        this.maxRetries = Math.max(1, maxRetries);
        this.signingLatency = Timer.builder("custody.sign.latency").register(meterRegistry);
        this.signingRequests = Counter.builder("custody.sign.requests").register(meterRegistry);
        this.signingSuccess = Counter.builder("custody.sign.success").register(meterRegistry);
        this.signingFailure = Counter.builder("custody.sign.failure").register(meterRegistry);
        this.retryCount = Counter.builder("custody.retry.count").register(meterRegistry);
        Gauge.builder("custody.queue.depth", queueRepository,
            repository -> repository.findTop50ByStatusInOrderByUpdatedAtAsc(List.of(
                WithdrawalQueueStatus.WAITING_SIGNER,
                WithdrawalQueueStatus.SIGN_FAILED
            )).size()
        ).register(meterRegistry);
    }

    @Transactional
    public void sign(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal was not found"));
        WithdrawalQueueItem queueItem = queueRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal queue item was not found"));
        validateReady(withdrawal, queueItem);
        if (queueItem.status() == WithdrawalQueueStatus.SIGN_FAILED) {
            retryCount.increment();
        }
        UnsignedTransaction unsignedTransaction = unsignedTransactionRepository.findByWithdrawalId(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("unsigned transaction was not found"));
        CustodyKey custodyKey = custodyKeyRepository.findByAssetCodeAndStatus(withdrawal.assetCode(), CustodyKeyStatus.ACTIVE)
            .orElseThrow(() -> new WalletValidationException("no active custody key is configured for " + withdrawal.assetCode()));
        TransactionSignatureAssembler assembler = assemblerRouter.requiredAssembler(withdrawal.assetCode());
        CustodyProvider provider = providerRouter.requiredProvider(custodyKey.provider());

        queueService.beginSigning(withdrawal, SIGNER_ACTOR);
        SigningRequest request = assembler.prepare(unsignedTransaction, custodyKey);
        signingRequests.increment();
        walletAuditService.record(WalletAuditEventType.CUSTODY_SIGNING_STARTED, withdrawal.id(), SIGNER_ACTOR, custodyKey.keyAlias());
        Instant start = clock.instant();
        try {
            SigningResult result = signingLatency.record(() -> provider.sign(request));
            SignedTransactionDraft signedDraft = assembler.assemble(unsignedTransaction, result, custodyKey);
            signedTransactionRepository.findByWithdrawalId(withdrawalId).orElseGet(() -> signedTransactionRepository.save(
                SignedTransaction.record(
                    withdrawal.id(),
                    unsignedTransaction.id(),
                    withdrawal.assetCode(),
                    withdrawal.networkCode(),
                    signedDraft.format(),
                    signedDraft.serializedPayload(),
                    signedDraft.signingDigest(),
                    signedDraft.signature(),
                    result.custodyProvider(),
                    custodyKey.keyAlias(),
                    custodyKey.keyVersion(),
                    custodyKey.algorithm(),
                    clock.instant()
                )
            ));
            recordAudit(withdrawal, custodyKey, provider.providerName(), Duration.between(start, clock.instant()).toMillis(), true, null);
            signingSuccess.increment();
            walletAuditService.record(WalletAuditEventType.CUSTODY_SIGNING_SUCCEEDED, withdrawal.id(), SIGNER_ACTOR, custodyKey.keyAlias());
            queueService.markSigned(withdrawal, SIGNER_ACTOR);
        } catch (RuntimeException exception) {
            signingFailure.increment();
            String reason = conciseReason(exception);
            recordAudit(withdrawal, custodyKey, provider.providerName(), Duration.between(start, clock.instant()).toMillis(), false, reason);
            walletAuditService.record(WalletAuditEventType.CUSTODY_SIGNING_FAILED, withdrawal.id(), SIGNER_ACTOR, reason);
            if (queueItem.signAttempts() + 1 >= maxRetries) {
                queueService.markSignFailed(withdrawal, SIGNER_ACTOR, reason);
                queueService.fail(withdrawal, SIGNER_ACTOR, "maximum custody signing retries exceeded");
            } else {
                queueService.markSignFailed(withdrawal, SIGNER_ACTOR, reason);
            }
        }
    }

    private void validateReady(Withdrawal withdrawal, WithdrawalQueueItem queueItem) {
        if (withdrawal.status() != WithdrawalStatus.APPROVED) {
            throw new WalletValidationException("withdrawal must be approved before custody signing");
        }
        if (queueItem.status() != WithdrawalQueueStatus.WAITING_SIGNER && queueItem.status() != WithdrawalQueueStatus.SIGN_FAILED) {
            throw new WalletValidationException("withdrawal queue is not ready for custody signing");
        }
    }

    private void recordAudit(
        Withdrawal withdrawal,
        CustodyKey custodyKey,
        String provider,
        long latencyMs,
        boolean success,
        String reason
    ) {
        signingAuditRepository.save(CustodySigningAuditEvent.record(
            withdrawal.id(),
            withdrawal.assetCode(),
            provider,
            custodyKey.keyAlias(),
            custodyKey.keyVersion(),
            custodyKey.algorithm(),
            latencyMs,
            success,
            reason,
            clock.instant()
        ));
    }

    private static String conciseReason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0, Math.min(500, message.length()));
    }
}
