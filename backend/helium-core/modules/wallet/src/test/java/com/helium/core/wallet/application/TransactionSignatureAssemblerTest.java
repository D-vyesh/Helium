package com.helium.core.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.CustodyKeyStatus;
import com.helium.core.wallet.domain.SigningAlgorithm;
import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionSignatureAssemblerTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void solanaAssemblerAttachesExternalEd25519SignatureToVersionedTransaction() {
        byte[] unsigned = new byte[1 + 64 + 3];
        unsigned[0] = 1;
        unsigned[65] = (byte) 0x80;
        unsigned[66] = 1;
        unsigned[67] = 2;
        UnsignedTransaction tx = unsigned("SOL", "SOL", "SOLANA_V0", Base64.getEncoder().encodeToString(unsigned), null, null, "blockhash");
        CustodyKey key = key("SOL", SigningAlgorithm.ED25519);
        SolanaVersionedTransactionSignatureAssembler assembler = new SolanaVersionedTransactionSignatureAssembler();

        SigningRequest request = assembler.prepare(tx, key);
        byte[] signature = repeatedBytes(64, (byte) 7);
        SignedTransactionDraft signed = assembler.assemble(tx, new SigningResult("TEST", Base64.getEncoder().encodeToString(signature), null, Duration.ZERO), key);

        byte[] signedBytes = Base64.getDecoder().decode(signed.serializedPayload());
        assertThat(request.signingAlgorithm()).isEqualTo(SigningAlgorithm.ED25519);
        assertThat(signed.format()).isEqualTo("SIGNED_SOLANA_V0");
        assertThat(signedBytes).startsWith((byte) 1);
        assertThat(copy(signedBytes, 1, 65)).containsExactly(signature);
    }

    @Test
    void ethereumAssemblerCreatesSignedTypeTwoPayloadFromExternalEcdsaSignature() {
        String payload = "{"
            + "\"type\":\"EIP-1559\","
            + "\"chainId\":\"1\","
            + "\"nonce\":\"7\","
            + "\"gasLimit\":\"21000\","
            + "\"maxFeePerGas\":\"35000000000\","
            + "\"maxPriorityFeePerGas\":\"1500000000\","
            + "\"to\":\"0x2222222222222222222222222222222222222222\","
            + "\"value\":\"1250000000000000000\","
            + "\"data\":\"0x\""
            + "}";
        UnsignedTransaction tx = unsigned("ETH", "ETH", "EIP1559_JSON", payload, 7L, null, null);
        CustodyKey key = key("ETH", SigningAlgorithm.SECP256K1_ECDSA);
        EthereumEip1559SignatureAssembler assembler = new EthereumEip1559SignatureAssembler(new ObjectMapper());

        byte[] signature = new byte[65];
        signature[31] = 1;
        signature[63] = 2;
        signature[64] = 1;
        SignedTransactionDraft signed = assembler.assemble(tx, new SigningResult("TEST", "0x" + HexFormat.of().formatHex(signature), null, Duration.ZERO), key);

        assertThat(assembler.prepare(tx, key).signingAlgorithm()).isEqualTo(SigningAlgorithm.SECP256K1_ECDSA);
        assertThat(signed.format()).isEqualTo("SIGNED_EIP1559");
        assertThat(signed.serializedPayload()).startsWith("0x02");
        assertThat(signed.signingDigest()).hasSize(64);
    }

    @Test
    void routerRejectsMissingAssembler() {
        TransactionSignatureAssemblerRouter router = new TransactionSignatureAssemblerRouter(List.of(new SolanaVersionedTransactionSignatureAssembler()));

        assertThatThrownBy(() -> router.requiredAssembler("BTC"))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("no transaction signature assembler");
    }

    private static UnsignedTransaction unsigned(
        String asset,
        String network,
        String format,
        String payload,
        Long nonce,
        String psbt,
        String blockhash
    ) {
        return UnsignedTransaction.built(
            UUID.randomUUID(),
            asset,
            network,
            format,
            "test-v1",
            payload,
            psbt,
            nonce,
            blockhash,
            BigDecimal.ZERO,
            "{}",
            NOW
        );
    }

    private static CustodyKey key(String asset, SigningAlgorithm algorithm) {
        return CustodyKey.register(asset, "alias-" + asset, "v1", "TEST", algorithm, null, CustodyKeyStatus.ACTIVE, NOW);
    }

    private static byte[] repeatedBytes(int count, byte value) {
        byte[] bytes = new byte[count];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] copy(byte[] bytes, int from, int to) {
        byte[] copy = new byte[to - from];
        System.arraycopy(bytes, from, copy, 0, copy.length);
        return copy;
    }
}
