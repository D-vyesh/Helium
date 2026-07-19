package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EthereumAnvilLifecycleIntegrationTest extends AbstractBlockchainCertificationIntegrationTest {
    @Test
    void detectsConfirmsPostsAndReplaysRealAnvilEthDeposit() throws Exception {
        UUID userId = activeUser("eth-deposit-" + UUID.randomUUID() + "@cert.helium.local");
        String depositAddress = randomEthAddress();
        assignDepositAddress(userId, "ETH", "ETH", depositAddress);
        primeMonitor("ETH");

        String sender = eth("eth_accounts", List.of()).path(0).asText();
        BigDecimal amount = new BigDecimal("1.000000000000000000");
        String value = "0x" + amount.movePointRight(18).toBigIntegerExact().toString(16);
        String txHash = eth("eth_sendTransaction", List.of(Map.of(
            "from", sender,
            "to", depositAddress,
            "value", value
        ))).asText();
        mineEthereumBlocks(12);

        assertThat(eth("eth_getTransactionReceipt", List.of(txHash)).path("status").asText()).isEqualTo("0x1");

        awaitPostedDeposit("ETH", txHash);

        assertThat(balance("USER", userId.toString(), "ETH", "AVAILABLE")).isEqualByComparingTo(amount);
        assertThat(countRows("wallet_deposits", "network_code = 'ETH' and tx_hash = '" + txHash + "'")).isEqualTo(1);
        assertThat(countRows("wallet_chain_transaction_observations", "network_code = 'ETH' and tx_hash = '" + txHash + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);

        pollChains();

        assertThat(countRows("wallet_deposits", "network_code = 'ETH' and tx_hash = '" + txHash + "'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:deposit:%'")).isEqualTo(1);
        assertThat(balance("USER", userId.toString(), "ETH", "AVAILABLE")).isEqualByComparingTo(amount);
        assertThat(eth("eth_getBalance", List.of(depositAddress, "latest")).asText())
            .isEqualTo("0x" + amount.movePointRight(18).toBigIntegerExact().toString(16));
    }
}
