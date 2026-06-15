package com.helium.core.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_reconciliation_reports")
public class ReconciliationReport {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, updatable = false, length = 80)
    private ReconciliationReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private ReconciliationReportStatus status;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "scope_key", nullable = false, updatable = false, length = 160)
    private String scopeKey;

    @Column(name = "left_label", nullable = false, updatable = false, length = 80)
    private String leftLabel;

    @Column(name = "right_label", nullable = false, updatable = false, length = 80)
    private String rightLabel;

    @Column(name = "left_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal leftTotal;

    @Column(name = "right_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal rightTotal;

    @Column(name = "difference", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal difference;

    @Column(name = "created_by", nullable = false, updatable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationReport() {
    }

    private ReconciliationReport(
        ReconciliationReportType reportType,
        LocalDate businessDate,
        String scopeKey,
        String leftLabel,
        String rightLabel,
        BigDecimal leftTotal,
        BigDecimal rightTotal,
        String createdBy,
        Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.reportType = Objects.requireNonNull(reportType, "reportType");
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate");
        this.scopeKey = requireText(scopeKey, "scopeKey", 160);
        this.leftLabel = requireText(leftLabel, "leftLabel", 80);
        this.rightLabel = requireText(rightLabel, "rightLabel", 80);
        this.leftTotal = Objects.requireNonNull(leftTotal, "leftTotal").stripTrailingZeros();
        this.rightTotal = Objects.requireNonNull(rightTotal, "rightTotal").stripTrailingZeros();
        this.difference = this.leftTotal.subtract(this.rightTotal).stripTrailingZeros();
        this.status = this.difference.compareTo(BigDecimal.ZERO) == 0
            ? ReconciliationReportStatus.CLEAN
            : ReconciliationReportStatus.DISCREPANCY;
        this.createdBy = requireText(createdBy, "createdBy", 120);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ReconciliationReport compare(
        ReconciliationReportType reportType,
        LocalDate businessDate,
        String scopeKey,
        String leftLabel,
        String rightLabel,
        BigDecimal leftTotal,
        BigDecimal rightTotal,
        String createdBy,
        Instant createdAt
    ) {
        return new ReconciliationReport(reportType, businessDate, scopeKey, leftLabel, rightLabel, leftTotal, rightTotal, createdBy, createdAt);
    }

    public UUID id() {
        return id;
    }

    public ReconciliationReportType reportType() {
        return reportType;
    }

    public ReconciliationReportStatus status() {
        return status;
    }

    public String scopeKey() {
        return scopeKey;
    }

    public BigDecimal difference() {
        return difference;
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
