package com.helium.core.wallet.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.domain.BlockchainConsistencyIncident;
import com.helium.core.wallet.domain.BlockchainConsistencyIncidentStatus;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.BlockchainConsistencyIncidentRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockchainConsistencyIncidentService {
    private static final String SYSTEM_ACTOR = "system:blockchain-consensus";

    private final BlockchainConsistencyIncidentRepository repository;
    private final WalletAuditService auditService;
    private final WalletActorService actorService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BlockchainConsistencyIncidentService(
        BlockchainConsistencyIncidentRepository repository,
        WalletAuditService auditService,
        WalletActorService actorService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.actorService = actorService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public BlockchainConsistencyIncidentView openIfAbsent(
        String network,
        String transactionId,
        String incidentType,
        String expectedState,
        List<BlockchainTransactionObservation> observations
    ) {
        String normalizedNetwork = BlockchainNetwork.normalizeNetworkCode(network);
        return repository.findFirstByNetworkAndTransactionIdAndIncidentTypeAndStatusOrderByDetectedAtDesc(
            normalizedNetwork,
            transactionId,
            incidentType,
            BlockchainConsistencyIncidentStatus.OPEN
        ).map(BlockchainConsistencyIncidentService::toView)
            .orElseGet(() -> {
                BlockchainConsistencyIncident incident = repository.save(BlockchainConsistencyIncident.open(
                    normalizedNetwork,
                    transactionId,
                    incidentType,
                    expectedState,
                    observationsJson(observations),
                    clock.instant()
                ));
                auditService.record(WalletAuditEventType.RPC_PROVIDER_DISAGREEMENT, incident.id(), SYSTEM_ACTOR, incidentType);
                return toView(incident);
            });
    }

    @Transactional(readOnly = true)
    public List<BlockchainConsistencyIncidentView> listOpen() {
        return repository.findAllByStatusOrderByDetectedAtDesc(BlockchainConsistencyIncidentStatus.OPEN)
            .stream()
            .map(BlockchainConsistencyIncidentService::toView)
            .toList();
    }

    @Transactional(readOnly = true)
    public BlockchainConsistencyIncidentView get(UUID id) {
        return repository.findById(id)
            .map(BlockchainConsistencyIncidentService::toView)
            .orElseThrow(() -> new WalletValidationException("blockchain incident was not found"));
    }

    @Transactional
    public BlockchainConsistencyIncidentView acknowledge(UUID id, String notes) {
        String actorId = actorService.requireOperationsActor();
        BlockchainConsistencyIncident incident = repository.findById(id)
            .orElseThrow(() -> new WalletValidationException("blockchain incident was not found"));
        incident.acknowledge(actorId, notes, clock.instant());
        auditService.record(WalletAuditEventType.BLOCKCHAIN_INCIDENT_ACKNOWLEDGED, incident.id(), actorId, notes);
        return toView(incident);
    }

    @Transactional
    public BlockchainConsistencyIncidentView resolve(UUID id, String notes) {
        String actorId = actorService.requireOperationsActor();
        BlockchainConsistencyIncident incident = repository.findById(id)
            .orElseThrow(() -> new WalletValidationException("blockchain incident was not found"));
        incident.resolve(actorId, notes, clock.instant());
        auditService.record(WalletAuditEventType.BLOCKCHAIN_INCIDENT_RESOLVED, incident.id(), actorId, notes);
        return toView(incident);
    }

    private String observationsJson(List<BlockchainTransactionObservation> observations) {
        try {
            return objectMapper.writeValueAsString(observations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize blockchain observations", exception);
        }
    }

    private static BlockchainConsistencyIncidentView toView(BlockchainConsistencyIncident incident) {
        return new BlockchainConsistencyIncidentView(
            incident.id(),
            incident.network(),
            incident.transactionId(),
            incident.incidentType(),
            incident.expectedState(),
            incident.providerObservationsJson(),
            incident.status(),
            incident.detectedAt(),
            incident.resolvedAt(),
            incident.resolvedBy(),
            incident.resolutionNotes()
        );
    }
}
