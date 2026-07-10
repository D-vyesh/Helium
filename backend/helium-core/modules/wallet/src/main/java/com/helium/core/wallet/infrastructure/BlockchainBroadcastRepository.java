package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.BlockchainBroadcast;
import com.helium.core.wallet.domain.BlockchainBroadcastStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainBroadcastRepository extends JpaRepository<BlockchainBroadcast, UUID> {
    int countByWithdrawalId(UUID withdrawalId);

    Optional<BlockchainBroadcast> findFirstByWithdrawalIdAndStatusOrderByCreatedAtDesc(
        UUID withdrawalId,
        BlockchainBroadcastStatus status
    );

    List<BlockchainBroadcast> findTop100ByOrderByCreatedAtDesc();

    List<BlockchainBroadcast> findTop100ByStatusAndBroadcastedAtBeforeOrderByBroadcastedAtAsc(
        BlockchainBroadcastStatus status,
        Instant before
    );
}
