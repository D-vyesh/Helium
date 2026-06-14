package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.ChainMonitorState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChainMonitorStateRepository extends JpaRepository<ChainMonitorState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ChainMonitorState state where state.networkCode = :networkCode")
    Optional<ChainMonitorState> findByNetworkCodeForUpdate(@Param("networkCode") String networkCode);
}

