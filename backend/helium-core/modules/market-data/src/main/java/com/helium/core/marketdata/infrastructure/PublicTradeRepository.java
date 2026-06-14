package com.helium.core.marketdata.infrastructure;

import com.helium.core.marketdata.domain.PublicTrade;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicTradeRepository extends JpaRepository<PublicTrade, String> {
    List<PublicTrade> findByMarketSymbolAndTradedAtGreaterThanEqualOrderByTradedAtAscMarketSequenceAsc(String marketSymbol, Instant since);

    List<PublicTrade> findByMarketSymbolOrderByMarketSequenceAsc(String marketSymbol);
}
