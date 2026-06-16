package com.helium.core.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "status", nullable = false, length = 60)
    private String status;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportTicket() {}

    public SupportTicket(String userId, String subject) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.subject = subject;
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public void addInternalNote(String note) {
        if (this.internalNotes == null) {
            this.internalNotes = note;
        } else {
            this.internalNotes += "\n" + note;
        }
    }

    public void close() {
        this.status = "CLOSED";
    }

    public UUID id() { return id; }
    public String userId() { return userId; }
    public String subject() { return subject; }
    public String status() { return status; }
    public String internalNotes() { return internalNotes; }
}
