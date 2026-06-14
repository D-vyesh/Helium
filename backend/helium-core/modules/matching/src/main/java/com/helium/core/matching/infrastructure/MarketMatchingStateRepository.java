package com.helium.core.matching.infrastructure;

import com.helium.core.matching.domain.MarketMatchingState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMatchingStateRepository extends JpaRepository<MarketMatchingState, String> {
}
