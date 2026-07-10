package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.WithdrawalAuthorization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalAuthorizationRepository extends JpaRepository<WithdrawalAuthorization, UUID> {
    Optional<WithdrawalAuthorization> findByWithdrawalId(UUID withdrawalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authorization from WithdrawalAuthorization authorization where authorization.withdrawalId = :withdrawalId")
    Optional<WithdrawalAuthorization> findByWithdrawalIdForUpdate(@Param("withdrawalId") UUID withdrawalId);
}
