package com.helium.core.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockchainObservationConsensusServiceTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final BlockchainTransactionObservationService observationService = mock(BlockchainTransactionObservationService.class);
    private final BlockchainConsistencyIncidentService incidentService = mock(BlockchainConsistencyIncidentService.class);

    @Test
    void agreesOnlyWhenEnoughCanonicalProvidersMatch() {
        when(observationService.observe("BTC", "tx-1")).thenReturn(List.of(
            observation("btc-a", "block-1", 7),
            observation("btc-b", "block-1", 8)
        ));
        BlockchainObservationConsensusService service = service(2, 2);

        BlockchainObservationConsensus consensus = service.establish("BTC", "tx-1", "WITHDRAWAL_CONFIRMING");

        assertThat(consensus.status()).isEqualTo(BlockchainConsensusStatus.AGREED);
        assertThat(consensus.confirmations()).isEqualTo(7);
    }

    @Test
    void opensIncidentWhenProvidersDisagreeOnCanonicalState() {
        when(observationService.observe("BTC", "tx-2")).thenReturn(List.of(
            observation("btc-a", "block-a", 7),
            observation("btc-b", "block-b", 7)
        ));
        BlockchainObservationConsensusService service = service(2, 2);

        BlockchainObservationConsensus consensus = service.establish("BTC", "tx-2", "WITHDRAWAL_CONFIRMING");

        assertThat(consensus.status()).isEqualTo(BlockchainConsensusStatus.PROVIDER_DISAGREEMENT);
        verify(incidentService).openIfAbsent(
            eq("BTC"),
            eq("tx-2"),
            eq(BlockchainConsensusStatus.PROVIDER_DISAGREEMENT.name()),
            eq("WITHDRAWAL_CONFIRMING"),
            anyList()
        );
    }

    private BlockchainObservationConsensusService service(int minimumHealthyProviders, int minimumAgreement) {
        return new BlockchainObservationConsensusService(
            observationService,
            incidentService,
            new SimpleMeterRegistry(),
            minimumHealthyProviders,
            minimumAgreement
        );
    }

    private static BlockchainTransactionObservation observation(String providerId, String blockHash, long confirmations) {
        return new BlockchainTransactionObservation(
            "BTC",
            "tx",
            providerId,
            true,
            true,
            100L,
            blockHash,
            confirmations,
            "CONFIRMED",
            OBSERVED_AT
        );
    }
}
