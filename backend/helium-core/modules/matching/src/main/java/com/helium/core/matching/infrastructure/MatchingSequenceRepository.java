package com.helium.core.matching.infrastructure;

import com.helium.core.matching.domain.MatchingSequence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MatchingSequenceRepository extends JpaRepository<MatchingSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from MatchingSequence sequence where sequence.marketSymbol = :marketSymbol")
    Optional<MatchingSequence> findByMarketForUpdate(@Param("marketSymbol") String marketSymbol);
}
