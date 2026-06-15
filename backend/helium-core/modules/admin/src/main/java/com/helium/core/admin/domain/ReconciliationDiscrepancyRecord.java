package com.helium.core.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_reconciliation_discrepancies")
public class ReconciliationDiscrepancyRecord {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, updatable = false, length = 80)
    private ReconciliationReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 40)
    private DiscrepancySeverity severity;

    @Column(name = "scope_key", nullable = false, updatable = false, length = 160)
    private String scopeKey;

    @Column(name = "expected_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal expectedTotal;

    @Column(name = "actual_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal actualTotal;

    @Column(name = "difference", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal difference;

    @Column(name = "details", nullable = false, updatable = false, length = 1000)
    private String details;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected ReconciliationDiscrepancyRecord() {
    }

    private ReconciliationDiscrepancyRecord(
        UUID reportId,
        ReconciliationReportType reportType,
        DiscrepancySeverity severity,
        String scopeKey,
        BigDecimal expectedTotal,
        BigDecimal actualTotal,
        String details,
        Instant detectedAt
    ) {
        this.id = UUID.randomUUID();
        this.reportId = Objects.requireNonNull(reportId, "reportId");
        this.reportType = Objects.requireNonNull(reportType, "reportType");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.scopeKey = requireText(scopeKey, "scopeKey", 160);
        this.expectedTotal = Objects.requireNonNull(expectedTotal, "expectedTotal").stripTrailingZeros();
        this.actualTotal = Objects.requireNonNull(actualTotal, "actualTotal").stripTrailingZeros();
        this.difference = this.expectedTotal.subtract(this.actualTotal).stripTrailingZeros();
        this.details = requireText(details, "details", 1000);
        this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
    }

    public static ReconciliationDiscrepancyRecord record(
        UUID reportId,
        ReconciliationReportType reportType,
        DiscrepancySeverity severity,
        String scopeKey,
        BigDecimal expectedTotal,
        BigDecimal actualTotal,
        String details,
        Instant detectedAt
    ) {
        return new ReconciliationDiscrepancyRecord(reportId, reportType, severity, scopeKey, expectedTotal, actualTotal, details, detectedAt);
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new AdminValidationException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new AdminValidationException(field + " is too long");
        }
        return normalized;
    }
}
