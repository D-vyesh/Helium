package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.WithdrawalQueueItem;
import com.helium.core.wallet.domain.WithdrawalQueueStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalQueueRepository extends JpaRepository<WithdrawalQueueItem, UUID> {
    Optional<WithdrawalQueueItem> findByWithdrawalId(UUID withdrawalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from WithdrawalQueueItem item where item.withdrawalId = :withdrawalId")
    Optional<WithdrawalQueueItem> findByWithdrawalIdForUpdate(@Param("withdrawalId") UUID withdrawalId);

    List<WithdrawalQueueItem> findTop50ByStatusOrderByUpdatedAtAsc(WithdrawalQueueStatus status);

    List<WithdrawalQueueItem> findTop50ByStatusInOrderByUpdatedAtAsc(List<WithdrawalQueueStatus> statuses);
}
