package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.SignedTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignedTransactionRepository extends JpaRepository<SignedTransaction, UUID> {
    Optional<SignedTransaction> findByWithdrawalId(UUID withdrawalId);

    List<SignedTransaction> findTop100ByOrderBySignedAtDesc();
}
