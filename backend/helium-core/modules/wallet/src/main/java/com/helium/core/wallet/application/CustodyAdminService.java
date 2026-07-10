package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.CustodySigningAuditEvent;
import com.helium.core.wallet.domain.SignedTransaction;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.infrastructure.CustodyKeyRepository;
import com.helium.core.wallet.infrastructure.CustodySigningAuditEventRepository;
import com.helium.core.wallet.infrastructure.SignedTransactionRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustodyAdminService {
    private final CustodyKeyRepository custodyKeyRepository;
    private final SignedTransactionRepository signedTransactionRepository;
    private final CustodySigningAuditEventRepository auditEventRepository;
    private final WithdrawalQueueRepository queueRepository;

    public CustodyAdminService(
        CustodyKeyRepository custodyKeyRepository,
        SignedTransactionRepository signedTransactionRepository,
        CustodySigningAuditEventRepository auditEventRepository,
        WithdrawalQueueRepository queueRepository
    ) {
        this.custodyKeyRepository = custodyKeyRepository;
        this.signedTransactionRepository = signedTransactionRepository;
        this.auditEventRepository = auditEventRepository;
        this.queueRepository = queueRepository;
    }

    @Transactional(readOnly = true)
    public List<CustodyKeyAdminView> keys() {
        return custodyKeyRepository.findAllByOrderByAssetCodeAscKeyAliasAscKeyVersionAsc().stream()
            .map(this::toKeyView)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CustodySignatureAdminView> signatures() {
        return signedTransactionRepository.findTop100ByOrderBySignedAtDesc().stream()
            .map(this::toSignatureView)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SignatureStatusAdminView> signatureStatus(UUID withdrawalId) {
        Optional<WithdrawalQueueItem> queue = queueRepository.findByWithdrawalId(withdrawalId);
        Optional<SignedTransaction> signed = signedTransactionRepository.findByWithdrawalId(withdrawalId);
        if (queue.isEmpty() && signed.isEmpty()) {
            return Optional.empty();
        }
        WithdrawalQueueItem item = queue.orElse(null);
        SignedTransaction signedTransaction = signed.orElse(null);
        return Optional.of(new SignatureStatusAdminView(
            withdrawalId,
            item == null ? null : item.status(),
            item == null ? 0 : item.signAttempts(),
            item == null ? null : item.nextSignAttemptAt(),
            item == null ? null : item.lastError(),
            signedTransaction != null,
            signedTransaction == null ? null : signedTransaction.signedAt()
        ));
    }

    @Transactional(readOnly = true)
    public List<CustodyAuditAdminView> audit(UUID withdrawalId) {
        return auditEventRepository.findAllByWithdrawalIdOrderByOccurredAtDesc(withdrawalId).stream()
            .map(this::toAuditView)
            .toList();
    }

    private CustodyKeyAdminView toKeyView(CustodyKey key) {
        return new CustodyKeyAdminView(
            key.assetCode(),
            key.keyAlias(),
            key.keyVersion(),
            key.provider(),
            key.algorithm(),
            key.status(),
            key.publicKeyHex() != null,
            key.createdAt(),
            key.activatedAt(),
            key.retiredAt()
        );
    }

    private CustodySignatureAdminView toSignatureView(SignedTransaction transaction) {
        return new CustodySignatureAdminView(
            transaction.withdrawalId(),
            transaction.assetCode(),
            transaction.networkCode(),
            transaction.format(),
            transaction.signingDigest(),
            transaction.custodyProvider(),
            transaction.keyAlias(),
            transaction.keyVersion(),
            transaction.algorithm(),
            transaction.signedAt()
        );
    }

    private CustodyAuditAdminView toAuditView(CustodySigningAuditEvent event) {
        return new CustodyAuditAdminView(
            event.withdrawalId(),
            event.assetCode(),
            event.custodyProvider(),
            event.keyAlias(),
            event.keyVersion(),
            event.algorithm(),
            event.latencyMs(),
            event.success(),
            event.errorReason(),
            event.occurredAt()
        );
    }
}
