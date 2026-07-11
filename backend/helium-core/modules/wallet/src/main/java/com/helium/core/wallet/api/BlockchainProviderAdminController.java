package com.helium.core.wallet.api;

import com.helium.core.wallet.application.BlockchainHealthAdminView;
import com.helium.core.wallet.application.BlockchainChainCursorAdminView;
import com.helium.core.wallet.application.BlockchainConsistencyIncidentService;
import com.helium.core.wallet.application.BlockchainConsistencyIncidentView;
import com.helium.core.wallet.application.BlockchainIncidentResolutionRequest;
import com.helium.core.wallet.application.BlockchainProviderAdminService;
import com.helium.core.wallet.application.BlockchainProviderAdminView;
import com.helium.core.wallet.application.BlockchainTransactionObservation;
import com.helium.core.wallet.application.BlockchainTransactionObservationService;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.ChainMonitorStateRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/blockchain")
public class BlockchainProviderAdminController {
    private final BlockchainProviderAdminService adminService;
    private final BlockchainConsistencyIncidentService incidentService;
    private final BlockchainTransactionObservationService observationService;
    private final ChainMonitorStateRepository chainMonitorStateRepository;

    public BlockchainProviderAdminController(
        BlockchainProviderAdminService adminService,
        BlockchainConsistencyIncidentService incidentService,
        BlockchainTransactionObservationService observationService,
        ChainMonitorStateRepository chainMonitorStateRepository
    ) {
        this.adminService = adminService;
        this.incidentService = incidentService;
        this.observationService = observationService;
        this.chainMonitorStateRepository = chainMonitorStateRepository;
    }

    @GetMapping("/providers")
    public List<BlockchainProviderAdminView> providers() {
        return adminService.providers();
    }

    @GetMapping("/providers/{chain}")
    public List<BlockchainProviderAdminView> providersByChain(@PathVariable String chain) {
        return adminService.providers(chain);
    }

    @GetMapping("/health")
    public BlockchainHealthAdminView health() {
        return adminService.health();
    }

    @PostMapping("/providers/{providerId}/disable")
    public ResponseEntity<Void> disable(@PathVariable String providerId) {
        adminService.disable(providerId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/providers/{providerId}/enable")
    public ResponseEntity<Void> enable(@PathVariable String providerId) {
        adminService.enable(providerId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/incidents")
    public List<BlockchainConsistencyIncidentView> incidents() {
        return incidentService.listOpen();
    }

    @GetMapping("/incidents/{id}")
    public BlockchainConsistencyIncidentView incident(@PathVariable UUID id) {
        return incidentService.get(id);
    }

    @PostMapping("/incidents/{id}/acknowledge")
    public BlockchainConsistencyIncidentView acknowledge(
        @PathVariable UUID id,
        @RequestBody BlockchainIncidentResolutionRequest request
    ) {
        return incidentService.acknowledge(id, request.notes());
    }

    @PostMapping("/incidents/{id}/resolve")
    public BlockchainConsistencyIncidentView resolve(
        @PathVariable UUID id,
        @RequestBody BlockchainIncidentResolutionRequest request
    ) {
        return incidentService.resolve(id, request.notes());
    }

    @GetMapping("/chains/{network}/cursor")
    public BlockchainChainCursorAdminView cursor(@PathVariable String network) {
        return chainMonitorStateRepository.findById(network.toUpperCase())
            .map(state -> new BlockchainChainCursorAdminView(
                state.networkCode(),
                state.lastObservedBlockHeight(),
                state.lastConfirmedBlockHeight(),
                state.reorgCheckpointBlockHeight(),
                state.scanCheckpointBlockHeight(),
                state.lastSuccessfulProvider(),
                state.lastObservedBlockHash(),
                state.lastObservedParentHash(),
                state.deepReorgReviewRequired(),
                state.updatedAt()
            ))
            .orElseThrow(() -> new WalletValidationException("chain cursor was not found"));
    }

    @GetMapping("/transactions/{network}/{transactionId}/observations")
    public List<BlockchainTransactionObservation> observations(
        @PathVariable String network,
        @PathVariable String transactionId
    ) {
        return observationService.observe(network, transactionId);
    }
}
