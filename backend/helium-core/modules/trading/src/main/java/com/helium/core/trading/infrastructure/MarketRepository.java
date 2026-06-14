package com.helium.core.trading.infrastructure;

import com.helium.core.trading.domain.Market;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, String> {
    List<Market> findByEnabledTrueOrderBySymbolAsc();
}
