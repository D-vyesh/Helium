package com.helium.core.ledger.infrastructure;

import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccount;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByOwnerTypeAndOwnerIdAndAssetCodeAndBalanceType(
        LedgerAccountOwnerType ownerType,
        String ownerId,
        String assetCode,
        BalanceType balanceType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from LedgerAccount account where account.id in :ids order by account.id")
    List<LedgerAccount> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(
        value = """
            insert into ledger_accounts (
                id,
                owner_type,
                owner_id,
                asset_code,
                balance_type,
                status,
                negative_balance_allowed,
                created_at
            )
            values (
                :id,
                :ownerType,
                :ownerId,
                :assetCode,
                :balanceType,
                'ACTIVE',
                :negativeBalanceAllowed,
                :createdAt
            )
            on conflict (owner_type, owner_id, asset_code, balance_type) do nothing
            """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("ownerType") String ownerType,
        @Param("ownerId") String ownerId,
        @Param("assetCode") String assetCode,
        @Param("balanceType") String balanceType,
        @Param("negativeBalanceAllowed") boolean negativeBalanceAllowed,
        @Param("createdAt") Instant createdAt
    );
}
