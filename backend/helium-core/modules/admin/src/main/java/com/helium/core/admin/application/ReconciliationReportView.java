package com.helium.core.admin.application;

import com.helium.core.admin.domain.ReconciliationReportStatus;
import com.helium.core.admin.domain.ReconciliationReportType;
import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationReportView(
    UUID reportId,
    ReconciliationReportType reportType,
    ReconciliationReportStatus status,
    String scopeKey,
    BigDecimal difference
) {
}
