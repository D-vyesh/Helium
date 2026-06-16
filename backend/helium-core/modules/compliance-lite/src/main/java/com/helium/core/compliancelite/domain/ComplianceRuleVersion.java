package com.helium.core.compliancelite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "compliance_rule_versions")
public class ComplianceRuleVersion {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "jurisdiction", nullable = false, length = 10)
    private String jurisdiction;

    @Column(name = "rule_payload", nullable = false, columnDefinition = "text")
    private String rulePayload;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected ComplianceRuleVersion() {}

    public ComplianceRuleVersion(String jurisdiction, String rulePayload) {
        this.id = UUID.randomUUID();
        this.jurisdiction = jurisdiction;
        this.rulePayload = rulePayload;
        this.effectiveFrom = Instant.now();
    }

    public void archive() {
        this.effectiveTo = Instant.now();
    }

    public UUID getId() { return id; }
    public String getJurisdiction() { return jurisdiction; }
    public String getRulePayload() { return rulePayload; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
}
