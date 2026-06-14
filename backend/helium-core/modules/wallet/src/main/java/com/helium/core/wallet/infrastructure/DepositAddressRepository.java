package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.DepositAddress;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositAddressRepository extends JpaRepository<DepositAddress, UUID> {
    Optional<DepositAddress> findByUserIdAndAssetCodeAndNetworkCode(UUID userId, String assetCode, String networkCode);

    Optional<DepositAddress> findByAddressAndNetworkCode(String address, String networkCode);
}

