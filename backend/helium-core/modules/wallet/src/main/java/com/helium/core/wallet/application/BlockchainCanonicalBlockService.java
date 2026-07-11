package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainCanonicalBlock;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.infrastructure.BlockchainCanonicalBlockRepository;
import com.helium.core.wallet.infrastructure.blockchain.CanonicalBlockReference;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockchainCanonicalBlockService {
    private final BlockchainCanonicalBlockRepository blockRepository;
    private final BlockchainConsistencyIncidentService incidentService;
    private final WalletAuditService auditService;
    private final Clock clock;

    public BlockchainCanonicalBlockService(
        BlockchainCanonicalBlockRepository blockRepository,
        BlockchainConsistencyIncidentService incidentService,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.blockRepository = blockRepository;
        this.incidentService = incidentService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CanonicalBlockObservationResult observe(CanonicalBlockReference reference) {
        String network = BlockchainNetwork.normalizeNetworkCode(reference.network());
        return blockRepository.findByNetworkAndHeight(network, reference.height())
            .map(existing -> compare(existing, reference))
            .orElseGet(() -> {
                blockRepository.save(BlockchainCanonicalBlock.observe(
                    network,
                    reference.height(),
                    reference.blockHash(),
                    reference.parentHash(),
                    BlockchainConsensusStatus.AGREED,
                    clock.instant()
                ));
                return CanonicalBlockObservationResult.unchanged();
            });
    }

    private CanonicalBlockObservationResult compare(BlockchainCanonicalBlock existing, CanonicalBlockReference reference) {
        if (existing.blockHash().equals(reference.blockHash())) {
            return CanonicalBlockObservationResult.unchanged();
        }
        List<BlockchainTransactionObservation> observations = List.of(
            blockObservation(existing.network(), Long.toString(existing.height()), "previous", existing.blockHash(), existing.parentHash(), existing.observedAt()),
            blockObservation(reference.network(), Long.toString(reference.height()), "current", reference.blockHash(), reference.parentHash(), clock.instant())
        );
        incidentService.openIfAbsent(
            existing.network(),
            "block:" + existing.height(),
            BlockchainConsensusStatus.REORG_SUSPECTED.name(),
            "CANONICAL_BLOCK",
            observations
        );
        auditService.record(
            WalletAuditEventType.DEEP_REORG_DETECTED,
            existing.id(),
            "chain-monitor",
            existing.network() + ":" + existing.height()
        );
        return CanonicalBlockObservationResult.reorgSuspected(existing.blockHash(), reference.blockHash());
    }

    private static BlockchainTransactionObservation blockObservation(
        String network,
        String transactionId,
        String providerId,
        String blockHash,
        String parentHash,
        java.time.Instant observedAt
    ) {
        return new BlockchainTransactionObservation(
            network,
            transactionId,
            providerId,
            true,
            true,
            null,
            blockHash + "|parent=" + parentHash,
            null,
            "CANONICAL_BLOCK",
            observedAt
        );
    }

    public record CanonicalBlockObservationResult(
        boolean reorgSuspected,
        String previousBlockHash,
        String currentBlockHash
    ) {
        static CanonicalBlockObservationResult unchanged() {
            return new CanonicalBlockObservationResult(false, null, null);
        }

        static CanonicalBlockObservationResult reorgSuspected(String previousBlockHash, String currentBlockHash) {
            return new CanonicalBlockObservationResult(true, previousBlockHash, currentBlockHash);
        }
    }
}
