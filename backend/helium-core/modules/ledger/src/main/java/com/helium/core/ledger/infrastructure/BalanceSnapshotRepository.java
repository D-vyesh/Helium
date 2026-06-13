package com.helium.core.ledger.infrastructure;

import com.helium.core.ledger.domain.BalanceSnapshot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BalanceSnapshot> findByAccount_Id(UUID accountId);

    @Modifying
    @Query(
        value = """
            insert into ledger_balance_snapshots (
                id,
                account_id,
                asset_code,
                current_balance,
                version,
                updated_at
            )
            values (
                :id,
                :accountId,
                :assetCode,
                0,
                0,
                :updatedAt
            )
            on conflict (account_id) do nothing
            """,
        nativeQuery = true
    )
    int insertZeroIfAbsent(
        @Param("id") UUID id,
        @Param("accountId") UUID accountId,
        @Param("assetCode") String assetCode,
        @Param("updatedAt") Instant updatedAt
    );
}
