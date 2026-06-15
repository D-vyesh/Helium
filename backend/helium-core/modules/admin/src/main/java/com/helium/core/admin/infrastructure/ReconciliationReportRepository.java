package com.helium.core.admin.infrastructure;

import com.helium.core.admin.domain.ReconciliationReport;
import com.helium.core.admin.domain.ReconciliationReportType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {
    List<ReconciliationReport> findByBusinessDateOrderByCreatedAtDesc(LocalDate businessDate);

    List<ReconciliationReport> findByReportTypeOrderByCreatedAtDesc(ReconciliationReportType reportType);
}
