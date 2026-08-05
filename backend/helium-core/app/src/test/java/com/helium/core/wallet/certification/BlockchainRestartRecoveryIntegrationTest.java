package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "HELIUM_RUN_BLOCKCHAIN_CERTIFICATION", matches = "true")
class BlockchainRestartRecoveryIntegrationTest extends AbstractBlockchainCertificationIntegrationTest {
    @Test
    void resumesFromPersistedCursorAndDoesNotDuplicateDepositPostingAfterInterruption() throws Exception {
        mineBitcoinBlocks(101);
        UUID userId = activeUser("restart-btc-" + UUID.randomUUID() + "@cert.helium.local");
        String depositAddress = btcWallet("getnewaddress", List.of("restart-user", "bech32")).asText();
        assignDepositAddress(userId, "BTC", "BTC", depositAddress);
        primeMonitor("BTC");

        BigDecimal amount = new BigDecimal("0.02000000");
        String txId = btcWallet("sendtoaddress", List.of(depositAddress, amount)).asText();
        mineBitcoinBlocks(2);
        pollChains();

        assertThat(nullableString("select status from wallet_deposits where network_code = 'BTC' and tx_hash = ?", txId))
            .isEqualTo("PENDING_CONFIRMATIONS");
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isZero();
        Long checkpointBeforeRecovery = jdbcTemplate.queryForObject(
            "select scan_checkpoint_block_height from wallet_chain_monitor_states where network_code = 'BTC'",
            Long.class
        );

        mineBitcoinBlocks(4);
        awaitPostedDeposit("BTC", txId);
        pollChains();

        Long checkpointAfterRecovery = jdbcTemplate.queryForObject(
            "select scan_checkpoint_block_height from wallet_chain_monitor_states where network_code = 'BTC'",
            Long.class
        );
        assertThat(checkpointAfterRecovery).isGreaterThanOrEqualTo(checkpointBeforeRecovery);
        assertThat(balance("USER", userId.toString(), "BTC", "AVAILABLE")).isEqualByComparingTo(amount);
        assertThat(countRows("wallet_deposits", "network_code = 'BTC' and tx_hash = '" + txId + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);
    }
}
