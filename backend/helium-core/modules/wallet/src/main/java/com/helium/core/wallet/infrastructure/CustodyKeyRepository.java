package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.CustodyKeyStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodyKeyRepository extends JpaRepository<CustodyKey, UUID> {
    Optional<CustodyKey> findByAssetCodeAndStatus(String assetCode, CustodyKeyStatus status);

    List<CustodyKey> findAllByOrderByAssetCodeAscKeyAliasAscKeyVersionAsc();
}
