package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.ChainTransactionDirection;
import com.helium.core.wallet.domain.ChainTransactionObservation;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainTransactionObservationRepository extends JpaRepository<ChainTransactionObservation, UUID> {
    Optional<ChainTransactionObservation> findByNetworkCodeAndTxHashAndOutputIndex(
        String networkCode,
        String txHash,
        int outputIndex
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChainTransactionObservation> findByMatchedDepositId(UUID depositId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChainTransactionObservation> findByMatchedWithdrawalId(UUID withdrawalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select observation
        from ChainTransactionObservation observation
        where observation.networkCode = :networkCode
          and observation.txHash = :txHash
          and observation.outputIndex = :outputIndex
        """)
    Optional<ChainTransactionObservation> findByChainReferenceForUpdate(
        @Param("networkCode") String networkCode,
        @Param("txHash") String txHash,
        @Param("outputIndex") int outputIndex
    );

    @Query("""
        select coalesce(sum(observation.amount), 0)
        from ChainTransactionObservation observation
        where observation.assetCode = :assetCode
          and observation.networkCode = :networkCode
          and observation.direction = :direction
          and observation.confirmations >= :requiredConfirmations
        """)
    BigDecimal sumConfirmedAmount(
        @Param("assetCode") String assetCode,
        @Param("networkCode") String networkCode,
        @Param("direction") ChainTransactionDirection direction,
        @Param("requiredConfirmations") int requiredConfirmations
    );
}
