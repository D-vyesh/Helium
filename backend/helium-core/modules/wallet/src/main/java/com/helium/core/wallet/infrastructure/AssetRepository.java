package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {
}

