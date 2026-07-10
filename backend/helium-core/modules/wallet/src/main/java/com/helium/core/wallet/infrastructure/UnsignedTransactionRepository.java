package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.UnsignedTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnsignedTransactionRepository extends JpaRepository<UnsignedTransaction, UUID> {
    Optional<UnsignedTransaction> findByWithdrawalId(UUID withdrawalId);
}
