package com.helium.core.wallet.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.SigningAlgorithm;
import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;

@Component
public class EthereumEip1559SignatureAssembler implements TransactionSignatureAssembler {
    private final ObjectMapper objectMapper;

    public EthereumEip1559SignatureAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String assetCode() {
        return "ETH";
    }

    @Override
    public SigningRequest prepare(UnsignedTransaction unsignedTransaction, CustodyKey custodyKey) {
        if (custodyKey.algorithm() != SigningAlgorithm.SECP256K1_ECDSA) {
            throw new WalletValidationException("ETH custody key must use SECP256K1_ECDSA signing");
        }
        Eip1559Fields fields = fields(unsignedTransaction);
        byte[] signingPayload = signingPayload(fields);
        byte[] digest = Hash.sha3(signingPayload);
        return new SigningRequest(
            "ETH",
            unsignedTransaction.withdrawalId(),
            unsignedTransaction.serializedPayload(),
            CryptoEncoding.hex(digest),
            Base64.getEncoder().encodeToString(digest),
            custodyKey.keyAlias(),
            custodyKey.keyVersion(),
            custodyKey.algorithm(),
            "{\"format\":\"EIP1559\",\"chainId\":\"" + fields.chainId() + "\"}"
        );
    }

    @Override
    public SignedTransactionDraft assemble(UnsignedTransaction unsignedTransaction, SigningResult signingResult, CustodyKey custodyKey) {
        Eip1559Fields fields = fields(unsignedTransaction);
        byte[] signingPayload = signingPayload(fields);
        byte[] digest = Hash.sha3(signingPayload);
        EcdsaSignature signature = EcdsaSignature.parse(signingResult.signature());
        byte[] signedPayload = prefixTypedTransaction(rlpList(
            uint(fields.chainId()),
            uint(fields.nonce()),
            uint(fields.maxPriorityFeePerGas()),
            uint(fields.maxFeePerGas()),
            uint(fields.gasLimit()),
            bytes(CryptoEncoding.decodeHex(fields.to(), "to")),
            uint(fields.value()),
            bytes(CryptoEncoding.decodeHex(fields.data(), "data")),
            rlpList(),
            uint(BigInteger.valueOf(signature.yParity())),
            uint(signature.r()),
            uint(signature.s())
        ));
        return new SignedTransactionDraft(
            "SIGNED_EIP1559",
            CryptoEncoding.prefixedHex(signedPayload),
            CryptoEncoding.hex(digest),
            signingResult.signature(),
            custodyKey.algorithm()
        );
    }

    private Eip1559Fields fields(UnsignedTransaction unsignedTransaction) {
        try {
            JsonNode json = objectMapper.readTree(unsignedTransaction.serializedPayload());
            return new Eip1559Fields(
                new BigInteger(json.path("chainId").asText()),
                new BigInteger(json.path("nonce").asText()),
                new BigInteger(json.path("gasLimit").asText()),
                new BigInteger(json.path("maxFeePerGas").asText()),
                new BigInteger(json.path("maxPriorityFeePerGas").asText()),
                json.path("to").asText(),
                new BigInteger(json.path("value").asText()),
                json.path("data").asText("0x")
            );
        } catch (Exception exception) {
            throw new WalletValidationException("ETH unsigned transaction payload is invalid");
        }
    }

    private static byte[] signingPayload(Eip1559Fields fields) {
        return prefixTypedTransaction(rlpList(
            uint(fields.chainId()),
            uint(fields.nonce()),
            uint(fields.maxPriorityFeePerGas()),
            uint(fields.maxFeePerGas()),
            uint(fields.gasLimit()),
            bytes(CryptoEncoding.decodeHex(fields.to(), "to")),
            uint(fields.value()),
            bytes(CryptoEncoding.decodeHex(fields.data(), "data")),
            rlpList()
        ));
    }

    private static byte[] prefixTypedTransaction(byte[] rlp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x02);
        out.writeBytes(rlp);
        return out.toByteArray();
    }

    private static byte[] uint(BigInteger value) {
        if (value.signum() < 0) {
            throw new WalletValidationException("Ethereum numeric field cannot be negative");
        }
        if (BigInteger.ZERO.equals(value)) {
            return bytes(new byte[0]);
        }
        byte[] encoded = value.toByteArray();
        int start = encoded[0] == 0 ? 1 : 0;
        byte[] clean = new byte[encoded.length - start];
        System.arraycopy(encoded, start, clean, 0, clean.length);
        return bytes(clean);
    }

    private static byte[] bytes(byte[] value) {
        return encode(value, 0x80);
    }

    private static byte[] rlpList(byte[]... values) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (byte[] value : values) {
            payload.writeBytes(value);
        }
        return encode(payload.toByteArray(), 0xc0);
    }

    private static byte[] encode(byte[] payload, int offset) {
        if (offset == 0x80 && payload.length == 1 && (payload[0] & 0xff) < 0x80) {
            return payload;
        }
        if (payload.length <= 55) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(offset + payload.length);
            out.writeBytes(payload);
            return out.toByteArray();
        }
        byte[] length = unsigned(BigInteger.valueOf(payload.length));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(offset + 55 + length.length);
        out.writeBytes(length);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] encoded = value.toByteArray();
        int start = encoded[0] == 0 ? 1 : 0;
        byte[] clean = new byte[encoded.length - start];
        System.arraycopy(encoded, start, clean, 0, clean.length);
        return clean;
    }

    private record Eip1559Fields(
        BigInteger chainId,
        BigInteger nonce,
        BigInteger gasLimit,
        BigInteger maxFeePerGas,
        BigInteger maxPriorityFeePerGas,
        String to,
        BigInteger value,
        String data
    ) {}

    private record EcdsaSignature(int yParity, BigInteger r, BigInteger s) {
        private static EcdsaSignature parse(String signature) {
            byte[] bytes = CryptoEncoding.decodeSignature(signature);
            if (bytes.length != 65) {
                throw new WalletValidationException("ETH custody signature must be 65 bytes");
            }
            byte recovery = bytes[64];
            int parity;
            if (recovery == 27 || recovery == 28) {
                parity = recovery - 27;
            } else if (recovery == 0 || recovery == 1) {
                parity = recovery;
            } else {
                throw new WalletValidationException("ETH custody signature recovery id must be 0/1 or 27/28");
            }
            byte[] rBytes = new byte[32];
            byte[] sBytes = new byte[32];
            System.arraycopy(bytes, 0, rBytes, 0, 32);
            System.arraycopy(bytes, 32, sBytes, 0, 32);
            return new EcdsaSignature(parity, new BigInteger(1, rBytes), new BigInteger(1, sBytes));
        }
    }
}
