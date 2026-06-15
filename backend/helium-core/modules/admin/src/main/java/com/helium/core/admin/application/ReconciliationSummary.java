package com.helium.core.admin.application;

import java.util.List;

public record ReconciliationSummary(
    List<ReconciliationReportView> reports,
    long discrepancyCount
) {
}
