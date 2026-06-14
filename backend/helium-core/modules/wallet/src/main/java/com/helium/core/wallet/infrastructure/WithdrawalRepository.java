package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
    Optional<Withdrawal> findByUserIdAndClientRequestId(UUID userId, String clientRequestId);

    long countByAssetCodeAndNetworkCodeAndStatus(String assetCode, String networkCode, WithdrawalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select withdrawal from Withdrawal withdrawal where withdrawal.id = :id")
    Optional<Withdrawal> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        select coalesce(sum(withdrawal.amount), 0)
        from Withdrawal withdrawal
        where withdrawal.assetCode = :assetCode
          and withdrawal.networkCode = :networkCode
          and withdrawal.status = :status
        """)
    BigDecimal sumAmountByAssetCodeAndNetworkCodeAndStatus(
        @Param("assetCode") String assetCode,
        @Param("networkCode") String networkCode,
        @Param("status") WithdrawalStatus status
    );
}
