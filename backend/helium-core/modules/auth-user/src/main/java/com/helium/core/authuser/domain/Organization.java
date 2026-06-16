package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "kyb_status", nullable = false, length = 60)
    private String kybStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Organization() {}

    public Organization(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.kybStatus = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String kybStatus() { return kybStatus; }

    public void approveKyb() {
        this.kybStatus = "APPROVED";
    }
}
