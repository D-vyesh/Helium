package com.helium.core.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.blockchain.EthereumRpcClient;
import com.helium.core.wallet.infrastructure.blockchain.SolanaRpcClient;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnsignedTransactionBuilderTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ETH_SOURCE = "0x1111111111111111111111111111111111111111";
    private static final String ETH_DESTINATION = "0x2222222222222222222222222222222222222222";
    private static final String SOL_FEE_PAYER = base58PublicKey((byte) 1);
    private static final String SOL_DESTINATION = base58PublicKey((byte) 2);

    @Test
    void ethereumBuilderCreatesUnsignedEip1559PayloadFromLiveRpcDataShape() {
        EthereumTransactionBuilder builder = new EthereumTransactionBuilder(
            new TestEthereumRpcClient(),
            ETH_SOURCE
        );

        UnsignedTransactionDraft draft = builder.build(withdrawal("ETH", "ETH", ETH_DESTINATION, "1.250000000000000000", "0.020000000000000000"), FeeTier.FAST);

        assertThat(draft.format()).isEqualTo("EIP1559_JSON");
        assertThat(draft.builderVersion()).isEqualTo("eth-eip1559-v1");
        assertThat(draft.serializedPayload()).contains(
            "\"type\":\"EIP-1559\"",
            "\"chainId\":\"1\"",
            "\"nonce\":\"7\"",
            "\"gasLimit\":\"21000\"",
            "\"maxFeePerGas\":\"35000000000\"",
            "\"maxPriorityFeePerGas\":\"1500000000\"",
            "\"to\":\"" + ETH_DESTINATION + "\"",
            "\"value\":\"1250000000000000000\"",
            "\"data\":\"0x\""
        );
        assertThat(draft.nonce()).isEqualTo(7L);
        assertThat(draft.fee()).isEqualByComparingTo("0.000735");
        assertThat(draft.psbt()).isNull();
        assertThat(draft.recentBlockhash()).isNull();
    }

    @Test
    void solanaBuilderCreatesUnsignedVersionedTransactionWithBlockhashAndPriorityFee() {
        SolanaTransactionBuilder builder = new SolanaTransactionBuilder(
            new TestSolanaRpcClient(),
            SOL_FEE_PAYER
        );

        UnsignedTransactionDraft draft = builder.build(withdrawal("SOL", "SOL", SOL_DESTINATION, "0.500000000", "0.000010000"), FeeTier.MEDIUM);
        byte[] serialized = Base64.getDecoder().decode(draft.serializedPayload());

        assertThat(draft.format()).isEqualTo("SOLANA_V0");
        assertThat(draft.builderVersion()).isEqualTo("solana-v0-v1");
        assertThat(draft.recentBlockhash()).isEqualTo(SOL_FEE_PAYER);
        assertThat(draft.fee()).isEqualByComparingTo("0.000005");
        assertThat(draft.nonce()).isNull();
        assertThat(draft.psbt()).isNull();
        assertThat(serialized).hasSizeGreaterThan(64);
        assertThat(serialized[0]).isEqualTo((byte) 1);
    }

    @Test
    void routerDispatchesByAssetCode() {
        UnsignedTransactionBuilder btc = staticBuilder("BTC", "PSBT");
        UnsignedTransactionBuilder eth = staticBuilder("ETH", "EIP1559_JSON");
        UnsignedTransactionBuilder sol = staticBuilder("SOL", "SOLANA_V0");
        UnsignedTransactionBuilderRouter router = new UnsignedTransactionBuilderRouter(List.of(btc, eth, sol));

        assertThat(router.requiredBuilder("btc")).isSameAs(btc);
        assertThat(router.requiredBuilder("ETH")).isSameAs(eth);
        assertThat(router.requiredBuilder("sol")).isSameAs(sol);
        assertThatThrownBy(() -> router.requiredBuilder("USDT"))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("no unsigned transaction builder is configured");
    }

    private static UnsignedTransactionBuilder staticBuilder(String assetCode, String format) {
        return new UnsignedTransactionBuilder() {
            @Override
            public String assetCode() {
                return assetCode;
            }

            @Override
            public UnsignedTransactionDraft build(Withdrawal withdrawal, FeeTier feeTier) {
                return new UnsignedTransactionDraft(format, "test", "{}", null, null, null, BigDecimal.ZERO, "{}");
            }
        };
    }

    private static Withdrawal withdrawal(String asset, String network, String destination, String amount, String fee) {
        return Withdrawal.request(
            "client-" + asset,
            "0".repeat(64),
            UUID.randomUUID(),
            asset,
            network,
            destination,
            null,
            new BigDecimal(amount),
            new BigDecimal(fee),
            UUID.randomUUID(),
            NOW
        );
    }

    private static String base58PublicKey(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return encodeBase58(key);
    }

    private static String encodeBase58(byte[] bytes) {
        String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder encoded = new StringBuilder();
        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = number.divideAndRemainder(BigInteger.valueOf(58));
            encoded.append(alphabet.charAt(divRem[1].intValue()));
            number = divRem[0];
        }
        for (byte current : bytes) {
            if (current == 0) {
                encoded.append('1');
            } else {
                break;
            }
        }
        return encoded.reverse().toString();
    }

    private static final class TestEthereumRpcClient extends EthereumRpcClient {
        private TestEthereumRpcClient() {
            super(mock(CircuitBreakerClient.class), new ObjectMapper());
        }

        @Override
        public Eip1559BuildData transactionData(String from, String to, BigInteger valueWei) {
            assertThat(from).isEqualTo(ETH_SOURCE);
            assertThat(to).isEqualTo(ETH_DESTINATION);
            assertThat(valueWei).isEqualTo(new BigInteger("1250000000000000000"));
            return new Eip1559BuildData(
                BigInteger.ONE,
                BigInteger.valueOf(7),
                BigInteger.valueOf(21_000),
                new BigInteger("35000000000"),
                new BigInteger("1500000000")
            );
        }

        @Override
        public BigInteger getBalanceWei(String address) {
            assertThat(address).isEqualTo(ETH_SOURCE);
            return new BigInteger("2000000000000000000");
        }
    }

    private static final class TestSolanaRpcClient extends SolanaRpcClient {
        private TestSolanaRpcClient() {
            super(mock(CircuitBreakerClient.class), new ObjectMapper());
        }

        @Override
        public String getRecentBlockhash() {
            return SOL_FEE_PAYER;
        }

        @Override
        public BigDecimal estimatePriorityFeeLamports() {
            return BigDecimal.TEN;
        }

        @Override
        public BigDecimal getFeeForMessage(byte[] compiledMessage) {
            assertThat(compiledMessage).isNotEmpty();
            return new BigDecimal("0.000005");
        }

        @Override
        public BigDecimal getBalance(String address) {
            assertThat(address).isEqualTo(SOL_FEE_PAYER);
            return BigDecimal.ONE;
        }
    }
}
