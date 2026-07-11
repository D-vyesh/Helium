package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.BlockchainConsistencyIncident;
import com.helium.core.wallet.domain.BlockchainConsistencyIncidentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainConsistencyIncidentRepository extends JpaRepository<BlockchainConsistencyIncident, UUID> {
    List<BlockchainConsistencyIncident> findAllByStatusOrderByDetectedAtDesc(BlockchainConsistencyIncidentStatus status);

    Optional<BlockchainConsistencyIncident> findFirstByNetworkAndTransactionIdAndIncidentTypeAndStatusOrderByDetectedAtDesc(
        String network,
        String transactionId,
        String incidentType,
        BlockchainConsistencyIncidentStatus status
    );
}
