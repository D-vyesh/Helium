package com.helium.core.marketdata.infrastructure;

import com.helium.core.marketdata.domain.MarketDataSequence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MarketDataSequenceRepository extends JpaRepository<MarketDataSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from MarketDataSequence sequence where sequence.marketSymbol = :marketSymbol")
    Optional<MarketDataSequence> findByMarketForUpdate(@Param("marketSymbol") String marketSymbol);
}
