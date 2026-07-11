package com.helium.core.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helium.core.wallet.domain.BlockchainCanonicalBlock;
import com.helium.core.wallet.infrastructure.BlockchainCanonicalBlockRepository;
import com.helium.core.wallet.infrastructure.blockchain.CanonicalBlockReference;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlockchainCanonicalBlockServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final BlockchainCanonicalBlockRepository blockRepository = mock(BlockchainCanonicalBlockRepository.class);
    private final BlockchainConsistencyIncidentService incidentService = mock(BlockchainConsistencyIncidentService.class);
    private final WalletAuditService auditService = mock(WalletAuditService.class);

    @Test
    void persistsFirstObservedCanonicalBlock() {
        BlockchainCanonicalBlockService service = service();
        CanonicalBlockReference reference = new CanonicalBlockReference("BTC", 100, "block-a", "block-99");

        BlockchainCanonicalBlockService.CanonicalBlockObservationResult result = service.observe(reference);

        assertThat(result.reorgSuspected()).isFalse();
        verify(blockRepository).save(any(BlockchainCanonicalBlock.class));
    }

    @Test
    void opensIncidentWhenCanonicalIdentityChanges() {
        BlockchainCanonicalBlock existing = BlockchainCanonicalBlock.observe(
            "BTC",
            100,
            "block-a",
            "block-99",
            BlockchainConsensusStatus.AGREED,
            CLOCK.instant()
        );
        when(blockRepository.findByNetworkAndHeight("BTC", 100)).thenReturn(Optional.of(existing));
        BlockchainCanonicalBlockService service = service();

        BlockchainCanonicalBlockService.CanonicalBlockObservationResult result =
            service.observe(new CanonicalBlockReference("BTC", 100, "block-b", "block-98"));

        assertThat(result.reorgSuspected()).isTrue();
        verify(incidentService).openIfAbsent(
            eq("BTC"),
            eq("block:100"),
            eq(BlockchainConsensusStatus.REORG_SUSPECTED.name()),
            eq("CANONICAL_BLOCK"),
            anyList()
        );
    }

    private BlockchainCanonicalBlockService service() {
        return new BlockchainCanonicalBlockService(blockRepository, incidentService, auditService, CLOCK);
    }
}
