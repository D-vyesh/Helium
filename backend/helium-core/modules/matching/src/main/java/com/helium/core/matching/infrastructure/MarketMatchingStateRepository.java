package com.helium.core.matching.infrastructure;

import com.helium.core.matching.domain.MarketMatchingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.helium.core.matching.domain.MarketMatchingStatus;

public interface MarketMatchingStateRepository extends JpaRepository<MarketMatchingState, String> {
    List<MarketMatchingState> findByStatusIn(List<MarketMatchingStatus> statuses);
}
