package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.helium.core.wallet.application.BlockchainCanonicalBlockService;
import com.helium.core.wallet.infrastructure.blockchain.CanonicalBlockReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CanonicalChainReorgIntegrationTest extends AbstractBlockchainCertificationIntegrationTest {
    @Autowired
    private BlockchainCanonicalBlockService canonicalBlockService;

    @Test
    void recordsConsistencyIncidentWhenBitcoinRegtestBlockHashChangesAtObservedHeight() {
        mineBitcoinBlocks(101);
        long observedHeight = btc("getblockcount", List.of()).asLong() + 1L;
        String originalHash = mineBitcoinBlocks(1);
        CanonicalBlockReference original = blockReference(observedHeight, originalHash);

        assertThat(canonicalBlockService.observe(original).reorgSuspected()).isFalse();

        btc("invalidateblock", List.of(originalHash));
        String replacementTip = mineBitcoinBlocks(2);
        String replacementHash = btc("getblockhash", List.of(observedHeight)).asText();
        assertThat(replacementHash).isNotEqualTo(originalHash);
        assertThat(replacementTip).isNotBlank();

        BlockchainCanonicalBlockService.CanonicalBlockObservationResult result =
            canonicalBlockService.observe(blockReference(observedHeight, replacementHash));

        assertThat(result.reorgSuspected()).isTrue();
        assertThat(result.previousBlockHash()).isEqualTo(originalHash);
        assertThat(result.currentBlockHash()).isEqualTo(replacementHash);
        assertThat(countRows(
            "blockchain_consistency_incidents",
            "network = 'BTC' and transaction_id = 'block:" + observedHeight + "' and incident_type = 'REORG_SUSPECTED'"
        )).isEqualTo(1);
    }

    private CanonicalBlockReference blockReference(long height, String blockHash) {
        JsonNode block = btc("getblock", List.of(blockHash, 1));
        return new CanonicalBlockReference(
            "BTC",
            height,
            blockHash,
            block.path("previousblockhash").isTextual() ? block.path("previousblockhash").asText() : null
        );
    }
}
