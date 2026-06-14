package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.BlockchainNetwork;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainNetworkRepository extends JpaRepository<BlockchainNetwork, String> {
    List<BlockchainNetwork> findAllByAssetCode(String assetCode);
}

