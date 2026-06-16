package com.helium.core.admin.domain;

import com.helium.core.authuser.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "governance_approval_requests")
public class ApprovalRequest {
    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_type", nullable = false, updatable = false, length = 60)
    private String requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private Status status;

    @Column(name = "maker_id", nullable = false, updatable = false, length = 120)
    private String makerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "checker_role", nullable = false, updatable = false, length = 60)
    private Role checkerRole;

    @Column(name = "checker_id", length = 120)
    private String checkerId;

    @Column(name = "payload", nullable = false, updatable = false)
    private String payloadJson; // JSON representation of the request data

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ApprovalRequest() {}

    public ApprovalRequest(String requestType, String makerId, Role checkerRole, String payloadJson) {
        this.id = UUID.randomUUID();
        this.requestType = requestType;
        this.status = Status.PENDING;
        this.makerId = makerId;
        this.checkerRole = checkerRole;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
    }

    public UUID id() { return id; }
    public String requestType() { return requestType; }
    public Status status() { return status; }
    public String makerId() { return makerId; }
    public Role checkerRole() { return checkerRole; }
    public String checkerId() { return checkerId; }
    public String payloadJson() { return payloadJson; }

    public void approve(String checkerId) {
        if (this.makerId.equals(checkerId)) {
            throw new IllegalArgumentException("Maker cannot be the Checker");
        }
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Request is not PENDING");
        }
        this.status = Status.APPROVED;
        this.checkerId = checkerId;
        this.resolvedAt = Instant.now();
    }

    public void reject(String checkerId) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Request is not PENDING");
        }
        this.status = Status.REJECTED;
        this.checkerId = checkerId;
        this.resolvedAt = Instant.now();
    }
}
