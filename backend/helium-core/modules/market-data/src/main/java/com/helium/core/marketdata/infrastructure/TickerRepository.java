package com.helium.core.marketdata.infrastructure;

import com.helium.core.marketdata.domain.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TickerRepository extends JpaRepository<Ticker, String> {
}
