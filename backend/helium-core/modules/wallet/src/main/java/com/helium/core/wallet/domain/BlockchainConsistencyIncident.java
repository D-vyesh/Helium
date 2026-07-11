package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "blockchain_consistency_incidents")
public class BlockchainConsistencyIncident {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "network", nullable = false, updatable = false, length = 40)
    private String network;

    @Column(name = "transaction_id", nullable = false, updatable = false, length = 160)
    private String transactionId;

    @Column(name = "incident_type", nullable = false, updatable = false, length = 60)
    private String incidentType;

    @Column(name = "expected_state", nullable = false, updatable = false, length = 80)
    private String expectedState;

    @Lob
    @Column(name = "provider_observations_json", nullable = false, updatable = false)
    private String providerObservationsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BlockchainConsistencyIncidentStatus status;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 120)
    private String resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BlockchainConsistencyIncident() {
    }

    private BlockchainConsistencyIncident(
        String network,
        String transactionId,
        String incidentType,
        String expectedState,
        String providerObservationsJson,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.network = BlockchainNetwork.normalizeNetworkCode(network);
        this.transactionId = BlockchainNetwork.requireText(transactionId, "transactionId", 160);
        this.incidentType = BlockchainNetwork.requireText(incidentType, "incidentType", 60);
        this.expectedState = BlockchainNetwork.requireText(expectedState, "expectedState", 80);
        this.providerObservationsJson = BlockchainNetwork.requireText(providerObservationsJson, "providerObservationsJson", 20_000);
        this.status = BlockchainConsistencyIncidentStatus.OPEN;
        this.detectedAt = Objects.requireNonNull(now, "now");
    }

    public static BlockchainConsistencyIncident open(
        String network,
        String transactionId,
        String incidentType,
        String expectedState,
        String providerObservationsJson,
        Instant now
    ) {
        return new BlockchainConsistencyIncident(network, transactionId, incidentType, expectedState, providerObservationsJson, now);
    }

    public void acknowledge(String actorId, String notes, Instant now) {
        if (status == BlockchainConsistencyIncidentStatus.RESOLVED) {
            throw new WalletValidationException("resolved incidents cannot be acknowledged");
        }
        status = BlockchainConsistencyIncidentStatus.ACKNOWLEDGED;
        resolvedBy = BlockchainNetwork.requireText(actorId, "actorId", 120);
        resolutionNotes = BlockchainNetwork.requireText(notes, "notes", 1000);
        resolvedAt = Objects.requireNonNull(now, "now");
    }

    public void resolve(String actorId, String notes, Instant now) {
        status = BlockchainConsistencyIncidentStatus.RESOLVED;
        resolvedBy = BlockchainNetwork.requireText(actorId, "actorId", 120);
        resolutionNotes = BlockchainNetwork.requireText(notes, "notes", 1000);
        resolvedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() { return id; }
    public String network() { return network; }
    public String transactionId() { return transactionId; }
    public String incidentType() { return incidentType; }
    public String expectedState() { return expectedState; }
    public String providerObservationsJson() { return providerObservationsJson; }
    public BlockchainConsistencyIncidentStatus status() { return status; }
    public Instant detectedAt() { return detectedAt; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolvedBy() { return resolvedBy; }
    public String resolutionNotes() { return resolutionNotes; }
}
