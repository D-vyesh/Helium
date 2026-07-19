package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolanaValidatorLifecycleIntegrationTest extends AbstractBlockchainCertificationIntegrationTest {
    @Test
    void detectsConfirmsPostsAndReplaysRealValidatorSolDeposit() throws Exception {
        UUID userId = activeUser("sol-deposit-" + UUID.randomUUID() + "@cert.helium.local");
        String depositAddress = randomSolanaAddress();
        assignDepositAddress(userId, "SOL", "SOL", depositAddress);
        primeMonitor("SOL");

        long lamports = 1_000_000_000L;
        String signature = sol("requestAirdrop", List.of(depositAddress, lamports)).asText();
        waitForSolanaSignature(signature);
        sol("getLatestBlockhash", List.of(Map.of("commitment", "finalized")));

        awaitPostedDeposit("SOL", signature);

        BigDecimal amount = BigDecimal.valueOf(lamports).movePointLeft(9).stripTrailingZeros();
        assertThat(balance("USER", userId.toString(), "SOL", "AVAILABLE")).isEqualByComparingTo(amount);
        assertThat(countRows("wallet_deposits", "network_code = 'SOL' and tx_hash = '" + signature + "'")).isEqualTo(1);
        assertThat(countRows("wallet_chain_transaction_observations", "network_code = 'SOL' and tx_hash = '" + signature + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);

        pollChains();

        assertThat(countRows("wallet_deposits", "network_code = 'SOL' and tx_hash = '" + signature + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);
        assertThat(balance("USER", userId.toString(), "SOL", "AVAILABLE")).isEqualByComparingTo(amount);
    }
}
