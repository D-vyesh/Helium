package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.WithdrawalReorgEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalReorgEventRepository extends JpaRepository<WithdrawalReorgEvent, UUID> {
    List<WithdrawalReorgEvent> findTop100ByOrderByDetectedAtDesc();
}
