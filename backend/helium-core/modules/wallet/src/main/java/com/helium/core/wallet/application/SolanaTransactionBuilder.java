package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.blockchain.SolanaRpcClient;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Compiles an unsigned Solana v0 transfer message using live validator fee and blockhash data. */
@Component
public class SolanaTransactionBuilder implements UnsignedTransactionBuilder {
    private static final BigDecimal LAMPORTS_PER_SOL = new BigDecimal("1000000000");
    private static final int COMPUTE_UNIT_LIMIT = 200_000;
    private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
    private static final String COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111";

    private final SolanaRpcClient solanaRpcClient;
    private final String feePayer;

    public SolanaTransactionBuilder(
        SolanaRpcClient solanaRpcClient,
        @Value("${helium.wallet.custody.sol.fee-payer:}") String feePayer
    ) {
        this.solanaRpcClient = solanaRpcClient;
        this.feePayer = feePayer == null ? "" : feePayer.trim();
    }

    @Override
    public String assetCode() {
        return "SOL";
    }

    @Override
    public UnsignedTransactionDraft build(Withdrawal withdrawal, FeeTier feeTier) {
        if (!"SOL".equals(withdrawal.networkCode())) {
            throw new WalletValidationException("SOL builder requires the SOL network");
        }
        if (feePayer.isBlank()) {
            throw new WalletValidationException("SOL custody fee payer is not configured");
        }
        long lamports = toLamports(withdrawal.amount());
        byte[] payer = Base58.decodePublicKey(feePayer, "SOL custody fee payer");
        byte[] destination = Base58.decodePublicKey(withdrawal.destinationAddress(), "SOL destination address");
        String recentBlockhash = solanaRpcClient.getRecentBlockhash();
        byte[] blockhash = Base58.decodePublicKey(recentBlockhash, "SOL recent blockhash");
        long priorityFeeMicroLamports = solanaRpcClient.estimatePriorityFeeLamports().longValueExact();

        byte[] message = compileMessage(payer, destination, blockhash, lamports, priorityFeeMicroLamports);
        BigDecimal networkFee = solanaRpcClient.getFeeForMessage(message);
        BigDecimal required = withdrawal.amount().add(networkFee);
        if (solanaRpcClient.getBalance(feePayer).compareTo(required) < 0) {
            throw new WalletValidationException("SOL custody fee payer has insufficient balance for withdrawal and network fee");
        }
        byte[] unsignedTransaction = serializeUnsignedTransaction(message);
        return new UnsignedTransactionDraft(
            "SOLANA_V0",
            "solana-v0-v1",
            Base64.getEncoder().encodeToString(unsignedTransaction),
            null,
            null,
            recentBlockhash,
            networkFee,
            "{\"feeTier\":\"" + feeTier + "\",\"feePayer\":\"" + feePayer
                + "\",\"computeUnitLimit\":" + COMPUTE_UNIT_LIMIT
                + ",\"priorityFeeMicroLamports\":" + priorityFeeMicroLamports + "}"
        );
    }

    private static long toLamports(BigDecimal amount) {
        try {
            return amount.multiply(LAMPORTS_PER_SOL).longValueExact();
        } catch (ArithmeticException exception) {
            throw new WalletValidationException("SOL withdrawal amount cannot be represented in lamports");
        }
    }

    private static byte[] compileMessage(
        byte[] feePayer,
        byte[] destination,
        byte[] recentBlockhash,
        long lamports,
        long priorityFeeMicroLamports
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x80); // versioned message, version 0
        out.write(1); // one required signer: the custody fee payer
        out.write(0); // no readonly signed accounts
        out.write(2); // system and compute-budget program accounts are readonly unsigned
        writeShortVec(out, 4);
        write(out, feePayer);
        write(out, destination);
        write(out, Base58.decodePublicKey(SYSTEM_PROGRAM, "system program"));
        write(out, Base58.decodePublicKey(COMPUTE_BUDGET_PROGRAM, "compute budget program"));
        write(out, recentBlockhash);
        writeShortVec(out, 3);
        writeInstruction(out, 3, List.of(), computeUnitLimitData());
        writeInstruction(out, 3, List.of(), computeUnitPriceData(priorityFeeMicroLamports));
        writeInstruction(out, 2, List.of(0, 1), transferData(lamports));
        writeShortVec(out, 0); // no address lookup tables for a native SOL transfer
        return out.toByteArray();
    }

    private static byte[] serializeUnsignedTransaction(byte[] message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShortVec(out, 1);
        write(out, new byte[64]); // required signature slot; custody fills it during signing
        write(out, message);
        return out.toByteArray();
    }

    private static void writeInstruction(ByteArrayOutputStream out, int programIdIndex, List<Integer> accountIndexes, byte[] data) {
        out.write(programIdIndex);
        writeShortVec(out, accountIndexes.size());
        for (int index : accountIndexes) {
            out.write(index);
        }
        writeShortVec(out, data.length);
        write(out, data);
    }

    private static byte[] computeUnitLimitData() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(2);
        writeLittleEndian(out, COMPUTE_UNIT_LIMIT, 4);
        return out.toByteArray();
    }

    private static byte[] computeUnitPriceData(long priorityFeeMicroLamports) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        writeLittleEndian(out, priorityFeeMicroLamports, 8);
        return out.toByteArray();
    }

    private static byte[] transferData(long lamports) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLittleEndian(out, 2, 4);
        writeLittleEndian(out, lamports, 8);
        return out.toByteArray();
    }

    private static void writeShortVec(ByteArrayOutputStream out, int value) {
        int remaining = value;
        do {
            int next = remaining & 0x7f;
            remaining >>>= 7;
            out.write(remaining == 0 ? next : next | 0x80);
        } while (remaining != 0);
    }

    private static void writeLittleEndian(ByteArrayOutputStream out, long value, int bytes) {
        for (int i = 0; i < bytes; i++) {
            out.write((int) (value >>> (8 * i)) & 0xff);
        }
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
    }

    private static final class Base58 {
        private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

        private static byte[] decodePublicKey(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new WalletValidationException(field + " is required");
            }
            BigInteger number = BigInteger.ZERO;
            for (char character : value.toCharArray()) {
                int index = ALPHABET.indexOf(character);
                if (index < 0) {
                    throw new WalletValidationException(field + " is not a Base58 value");
                }
                number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(index));
            }
            byte[] encoded = number.toByteArray();
            int leadingZeroes = 0;
            while (leadingZeroes < value.length() && value.charAt(leadingZeroes) == '1') {
                leadingZeroes++;
            }
            int start = encoded[0] == 0 ? 1 : 0;
            byte[] decoded = new byte[leadingZeroes + encoded.length - start];
            System.arraycopy(encoded, start, decoded, leadingZeroes, encoded.length - start);
            if (decoded.length != 32) {
                throw new WalletValidationException(field + " must decode to a 32-byte public key");
            }
            return decoded;
        }
    }
}
