package com.helium.core.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "security_incidents")
public class SecurityIncident {
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "status", nullable = false, length = 60)
    private String status;

    @Column(name = "postmortem_report")
    private String postmortemReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SecurityIncident() {}

    public SecurityIncident(String title, Severity severity) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.severity = severity;
        this.status = "INVESTIGATING";
        this.createdAt = Instant.now();
    }

    public void mitigate() {
        this.status = "MITIGATED";
    }

    public void closeWithPostmortem(String report) {
        this.status = "CLOSED";
        this.postmortemReport = report;
    }

    public UUID id() { return id; }
    public String title() { return title; }
    public Severity severity() { return severity; }
    public String status() { return status; }
}
