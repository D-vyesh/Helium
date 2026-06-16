package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.HdWalletChain;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HdWalletChainRepository extends JpaRepository<HdWalletChain, UUID> {
    Optional<HdWalletChain> findByNetworkCode(String networkCode);
}
