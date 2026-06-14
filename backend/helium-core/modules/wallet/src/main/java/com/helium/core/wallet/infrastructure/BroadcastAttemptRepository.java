package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.BroadcastAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastAttemptRepository extends JpaRepository<BroadcastAttempt, UUID> {
    int countByWithdrawalId(UUID withdrawalId);
}

