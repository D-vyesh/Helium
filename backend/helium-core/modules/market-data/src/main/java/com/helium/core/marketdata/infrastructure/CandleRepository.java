package com.helium.core.marketdata.infrastructure;

import com.helium.core.marketdata.domain.Candle;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleRepository extends JpaRepository<Candle, UUID> {
    Optional<Candle> findByMarketSymbolAndIntervalNameAndOpenTime(String marketSymbol, String intervalName, Instant openTime);
}
