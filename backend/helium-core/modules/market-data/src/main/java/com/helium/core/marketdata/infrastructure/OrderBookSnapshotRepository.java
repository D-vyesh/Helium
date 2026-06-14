package com.helium.core.marketdata.infrastructure;

import com.helium.core.marketdata.domain.OrderBookSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderBookSnapshotRepository extends JpaRepository<OrderBookSnapshot, UUID> {
    Optional<OrderBookSnapshot> findByMarketSymbolAndMarketSequence(String marketSymbol, long marketSequence);
}
