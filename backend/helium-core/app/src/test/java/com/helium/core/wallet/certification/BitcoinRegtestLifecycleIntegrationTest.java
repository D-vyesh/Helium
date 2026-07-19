package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BitcoinRegtestLifecycleIntegrationTest extends AbstractBlockchainCertificationIntegrationTest {
    @Test
    void detectsConfirmsPostsAndReplaysRealRegtestBitcoinDeposit() throws Exception {
        mineBitcoinBlocks(101);
        UUID userId = activeUser("btc-deposit-" + UUID.randomUUID() + "@cert.helium.local");
        String depositAddress = btcWallet("getnewaddress", List.of("helium-user", "bech32")).asText();
        assignDepositAddress(userId, "BTC", "BTC", depositAddress);
        primeMonitor("BTC");

        BigDecimal amount = new BigDecimal("0.01000000");
        String txId = btcWallet("sendtoaddress", List.of(depositAddress, amount)).asText();
        mineBitcoinBlocks(6);

        awaitPostedDeposit("BTC", txId);
        BigDecimal after = balance("USER", userId.toString(), "BTC", "AVAILABLE");

        assertThat(after).isEqualByComparingTo(amount);
        assertThat(countRows("wallet_deposits", "network_code = 'BTC' and tx_hash = '" + txId + "'")).isEqualTo(1);
        assertThat(countRows("wallet_chain_transaction_observations", "network_code = 'BTC' and tx_hash = '" + txId + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);

        pollChains();

        assertThat(countRows("wallet_deposits", "network_code = 'BTC' and tx_hash = '" + txId + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);
        assertThat(balance("USER", userId.toString(), "BTC", "AVAILABLE")).isEqualByComparingTo(amount);
    }
}
