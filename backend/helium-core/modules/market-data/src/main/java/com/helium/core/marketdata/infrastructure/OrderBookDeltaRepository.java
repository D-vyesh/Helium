package com.helium.core.marketdata.infrastructure;

import com.helium.core.marketdata.domain.OrderBookDelta;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderBookDeltaRepository extends JpaRepository<OrderBookDelta, UUID> {
    Optional<OrderBookDelta> findByMarketSymbolAndMarketSequenceAndSideAndPrice(String marketSymbol, long marketSequence, String side, java.math.BigDecimal price);
}
