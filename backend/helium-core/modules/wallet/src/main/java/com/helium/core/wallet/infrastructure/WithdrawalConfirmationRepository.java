package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.WithdrawalConfirmation;
import com.helium.core.wallet.domain.WithdrawalConfirmationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalConfirmationRepository extends JpaRepository<WithdrawalConfirmation, UUID> {
    Optional<WithdrawalConfirmation> findByWithdrawalId(UUID withdrawalId);

    List<WithdrawalConfirmation> findTop100ByStatusOrderByLastCheckedAtAsc(WithdrawalConfirmationStatus status);

    List<WithdrawalConfirmation> findTop100ByOrderByLastCheckedAtDesc();
}
