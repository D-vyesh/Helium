package com.helium.core.wallet.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BlockchainObservationConsensusService {
    private final BlockchainTransactionObservationService observationService;
    private final BlockchainConsistencyIncidentService incidentService;
    private final int minimumHealthyProviders;
    private final int minimumAgreement;
    private final Counter agreed;
    private final Counter disagreement;
    private final Counter insufficient;

    public BlockchainObservationConsensusService(
        BlockchainTransactionObservationService observationService,
        BlockchainConsistencyIncidentService incidentService,
        MeterRegistry meterRegistry,
        @Value("${helium.wallet.blockchain.consensus.minimum-healthy-providers:1}") int minimumHealthyProviders,
        @Value("${helium.wallet.blockchain.consensus.minimum-agreement:1}") int minimumAgreement
    ) {
        this.observationService = observationService;
        this.incidentService = incidentService;
        this.minimumHealthyProviders = Math.max(1, minimumHealthyProviders);
        this.minimumAgreement = Math.max(1, minimumAgreement);
        this.agreed = Counter.builder("blockchain.consensus.agreed").register(meterRegistry);
        this.disagreement = Counter.builder("blockchain.consensus.disagreement").register(meterRegistry);
        this.insufficient = Counter.builder("blockchain.consensus.insufficient").register(meterRegistry);
    }

    public BlockchainObservationConsensus establish(String network, String transactionId, String expectedState) {
        List<BlockchainTransactionObservation> observations = observationService.observe(network, transactionId);
        List<BlockchainTransactionObservation> canonical = observations.stream()
            .filter(BlockchainTransactionObservation::observed)
            .filter(BlockchainTransactionObservation::canonical)
            .toList();
        if (canonical.isEmpty()) {
            insufficient.increment();
            incidentService.openIfAbsent(network, transactionId, BlockchainConsensusStatus.TRANSACTION_NOT_FOUND.name(), expectedState, observations);
            return new BlockchainObservationConsensus(BlockchainConsensusStatus.TRANSACTION_NOT_FOUND, observations, 0, "transaction not found canonically");
        }
        if (canonical.size() < minimumHealthyProviders) {
            insufficient.increment();
            incidentService.openIfAbsent(network, transactionId, BlockchainConsensusStatus.INSUFFICIENT_PROVIDERS.name(), expectedState, observations);
            return new BlockchainObservationConsensus(BlockchainConsensusStatus.INSUFFICIENT_PROVIDERS, observations, minConfirmations(canonical), "insufficient canonical provider observations");
        }
        Map<String, List<BlockchainTransactionObservation>> groups = canonical.stream()
            .collect(Collectors.groupingBy(BlockchainTransactionObservation::agreementKey));
        List<BlockchainTransactionObservation> bestAgreement = groups.values().stream()
            .max(Comparator.comparingInt(List::size))
            .orElse(List.of());
        if (bestAgreement.size() < minimumAgreement) {
            disagreement.increment();
            incidentService.openIfAbsent(network, transactionId, BlockchainConsensusStatus.PROVIDER_DISAGREEMENT.name(), expectedState, observations);
            return new BlockchainObservationConsensus(BlockchainConsensusStatus.PROVIDER_DISAGREEMENT, observations, minConfirmations(canonical), "providers disagree on transaction state");
        }
        agreed.increment();
        return new BlockchainObservationConsensus(BlockchainConsensusStatus.AGREED, observations, minConfirmations(bestAgreement), "provider observations agreed");
    }

    private static long minConfirmations(List<BlockchainTransactionObservation> observations) {
        return observations.stream()
            .map(BlockchainTransactionObservation::confirmations)
            .filter(value -> value != null)
            .mapToLong(Long::longValue)
            .min()
            .orElse(0L);
    }
}
