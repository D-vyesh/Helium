package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainConsistencyIncidentStatus;
import java.time.Instant;
import java.util.UUID;

public record BlockchainConsistencyIncidentView(
    UUID id,
    String network,
    String transactionId,
    String incidentType,
    String expectedState,
    String providerObservationsJson,
    BlockchainConsistencyIncidentStatus status,
    Instant detectedAt,
    Instant resolvedAt,
    String resolvedBy,
    String resolutionNotes
) {}
