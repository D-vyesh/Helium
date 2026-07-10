package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.WithdrawalQueueTransition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalQueueTransitionRepository extends JpaRepository<WithdrawalQueueTransition, UUID> {}
