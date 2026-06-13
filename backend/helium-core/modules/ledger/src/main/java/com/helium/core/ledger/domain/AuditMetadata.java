package com.helium.core.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class AuditMetadata {
    @Column(name = "actor_id", nullable = false, length = 120)
    private String actorId;

    @Column(name = "source_module", nullable = false, length = 80)
    private String sourceModule;

    @Column(name = "correlation_id", nullable = false, length = 120)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 120)
    private String causationId;

    @Column(name = "audit_reason", nullable = false, length = 500)
    private String reason;

    protected AuditMetadata() {
    }

    private AuditMetadata(String actorId, String sourceModule, String correlationId, String causationId, String reason) {
        this.actorId = requireText(actorId, "actorId");
        this.sourceModule = requireText(sourceModule, "sourceModule");
        this.correlationId = requireText(correlationId, "correlationId");
        this.causationId = requireText(causationId, "causationId");
        this.reason = requireText(reason, "reason");
    }

    public static AuditMetadata of(String actorId, String sourceModule, String correlationId, String causationId, String reason) {
        return new AuditMetadata(actorId, sourceModule, correlationId, causationId, reason);
    }

    public String actorId() {
        return actorId;
    }

    public String sourceModule() {
        return sourceModule;
    }

    public String correlationId() {
        return correlationId;
    }

    public String causationId() {
        return causationId;
    }

    public String reason() {
        return reason;
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new LedgerValidationException(field + " is required");
        }
        return value;
    }
}

