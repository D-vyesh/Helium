package com.helium.core.trading.infrastructure;

import com.helium.core.trading.domain.FeeSchedule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, UUID> {
    Optional<FeeSchedule> findByMarketSymbol(String marketSymbol);
}
