package com.helium.core.admin.infrastructure;

import com.helium.core.admin.domain.ReconciliationDiscrepancyRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationDiscrepancyRecordRepository extends JpaRepository<ReconciliationDiscrepancyRecord, UUID> {
    List<ReconciliationDiscrepancyRecord> findByReportId(UUID reportId);
}
