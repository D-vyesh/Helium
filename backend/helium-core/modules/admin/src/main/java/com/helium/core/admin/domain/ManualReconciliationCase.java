package com.helium.core.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_manual_reconciliation_cases")
public class ManualReconciliationCase {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "discrepancy_id", nullable = false, updatable = false)
    private UUID discrepancyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ManualReconciliationStatus status;

    @Column(name = "opened_by", nullable = false, updatable = false, length = 120)
    private String openedBy;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 120)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ManualReconciliationCase() {
    }

    private ManualReconciliationCase(UUID discrepancyId, String openedBy, Instant openedAt) {
        this.id = UUID.randomUUID();
        this.discrepancyId = Objects.requireNonNull(discrepancyId, "discrepancyId");
        this.status = ManualReconciliationStatus.OPEN;
        this.openedBy = requireText(openedBy, "openedBy", 120);
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
    }

    public static ManualReconciliationCase open(UUID discrepancyId, String openedBy, Instant openedAt) {
        return new ManualReconciliationCase(discrepancyId, openedBy, openedAt);
    }

    public void resolve(String notes, String actorId, Instant resolvedAt) {
        if (status != ManualReconciliationStatus.OPEN) {
            throw new AdminValidationException("manual reconciliation case is not open");
        }
        this.status = ManualReconciliationStatus.RESOLVED;
        this.resolutionNotes = requireText(notes, "notes", 1000);
        this.resolvedBy = requireText(actorId, "actorId", 120);
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
    }

    public UUID id() {
        return id;
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
