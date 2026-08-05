package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import com.helium.core.wallet.application.BlockchainConsistencyIncidentService;
import com.helium.core.wallet.application.BlockchainConsistencyIncidentView;
import com.helium.core.wallet.application.BlockchainTransactionObservation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "HELIUM_RUN_BLOCKCHAIN_CERTIFICATION", matches = "true")
class BlockchainProviderDisagreementIntegrationTest extends AbstractBlockchainCertificationIntegrationTest {
    @Autowired
    private BlockchainConsistencyIncidentService incidentService;

    @Test
    void providerDisagreementIncidentDoesNotCreateFinancialConfirmation() {
        String sender = eth("eth_accounts", List.of()).path(0).asText();
        String recipient = randomEthAddress();
        String txHash = eth("eth_sendTransaction", List.of(Map.of(
            "from", sender,
            "to", recipient,
            "value", "0x1"
        ))).asText();
        mineEthereumBlocks(1);
        String realBlockHash = eth("eth_getTransactionReceipt", List.of(txHash)).path("blockHash").asText();

        BlockchainConsistencyIncidentView incident = incidentService.openIfAbsent(
            "ETH",
            txHash,
            "PROVIDER_DISAGREEMENT",
            "DEPOSIT_CONFIRMING",
            List.of(
                new BlockchainTransactionObservation("ETH", txHash, "anvil-primary", true, true, 1L, realBlockHash, 1L, "SUCCESS", Instant.now()),
                new BlockchainTransactionObservation("ETH", txHash, "anvil-disagreement-control", true, true, 1L, "0xdeadbeef", 1L, "SUCCESS", Instant.now())
            )
        );

        assertThat(incident.incidentType()).isEqualTo("PROVIDER_DISAGREEMENT");
        assertThat(incident.status().name()).isEqualTo("OPEN");
        assertThat(countRows("blockchain_consistency_incidents", "transaction_id = '" + txHash + "'")).isEqualTo(1);
        assertThat(countRows("wallet_deposits", "tx_hash = '" + txHash + "'")).isZero();
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isZero();
    }
}
