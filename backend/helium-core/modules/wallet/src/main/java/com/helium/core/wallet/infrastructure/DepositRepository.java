package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.Deposit;
import com.helium.core.wallet.domain.DepositStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {
    Optional<Deposit> findByNetworkCodeAndTxHashAndOutputIndex(String networkCode, String txHash, int outputIndex);

    long countByAssetCodeAndNetworkCodeAndStatus(String assetCode, String networkCode, DepositStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deposit from Deposit deposit where deposit.id = :id")
    Optional<Deposit> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        select coalesce(sum(deposit.amount), 0)
        from Deposit deposit
        where deposit.assetCode = :assetCode
          and deposit.networkCode = :networkCode
          and deposit.status = :status
        """)
    BigDecimal sumAmountByAssetCodeAndNetworkCodeAndStatus(
        @Param("assetCode") String assetCode,
        @Param("networkCode") String networkCode,
        @Param("status") DepositStatus status
    );
}
