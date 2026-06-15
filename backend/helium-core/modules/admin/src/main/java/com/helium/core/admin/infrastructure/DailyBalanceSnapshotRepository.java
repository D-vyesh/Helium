package com.helium.core.admin.infrastructure;

import com.helium.core.admin.domain.DailyBalanceSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyBalanceSnapshotRepository extends JpaRepository<DailyBalanceSnapshot, UUID> {
    List<DailyBalanceSnapshot> findByBusinessDate(LocalDate businessDate);
}
