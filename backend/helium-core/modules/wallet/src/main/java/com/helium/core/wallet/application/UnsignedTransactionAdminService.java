package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.infrastructure.UnsignedTransactionRepository;
import com.helium.core.wallet.infrastructure.WithdrawalQueueRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnsignedTransactionAdminService {
    private final UnsignedTransactionRepository unsignedTransactionRepository;
    private final WithdrawalQueueRepository queueRepository;

    public UnsignedTransactionAdminService(
        UnsignedTransactionRepository unsignedTransactionRepository,
        WithdrawalQueueRepository queueRepository
    ) {
        this.unsignedTransactionRepository = unsignedTransactionRepository;
        this.queueRepository = queueRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UnsignedTransactionAdminView> find(UUID withdrawalId) {
        Optional<WithdrawalQueueItem> queue = queueRepository.findByWithdrawalId(withdrawalId);
        Optional<UnsignedTransaction> transaction = unsignedTransactionRepository.findByWithdrawalId(withdrawalId);
        if (queue.isEmpty() && transaction.isEmpty()) {
            return Optional.empty();
        }
        WithdrawalQueueItem item = queue.orElseThrow();
        UnsignedTransaction draft = transaction.orElse(null);
        return Optional.of(new UnsignedTransactionAdminView(
            draft == null ? null : draft.assetCode(),
            draft == null ? null : draft.networkCode(),
            item.status(),
            draft == null ? null : draft.format(),
            draft == null ? null : draft.builderVersion(),
            draft == null ? null : draft.fee(),
            draft == null ? null : draft.nonce(),
            draft == null ? null : draft.recentBlockhash(),
            draft != null && draft.psbt() != null,
            draft == null ? 0 : draft.serializedPayload().getBytes(StandardCharsets.UTF_8).length,
            draft == null ? null : draft.builtAt(),
            item.buildAttempts(),
            item.nextBuildAttemptAt(),
            item.lastError()
        ));
    }
}
