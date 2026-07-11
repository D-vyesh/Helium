package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.BlockchainCanonicalBlock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainCanonicalBlockRepository extends JpaRepository<BlockchainCanonicalBlock, UUID> {
    Optional<BlockchainCanonicalBlock> findByNetworkAndHeight(String network, long height);

    List<BlockchainCanonicalBlock> findAllByNetworkOrderByHeightDesc(String network);
}
