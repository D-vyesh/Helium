package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.SigningAlgorithm;
import com.helium.core.wallet.domain.UnsignedTransaction;
import com.helium.core.wallet.domain.WalletValidationException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SolanaVersionedTransactionSignatureAssembler implements TransactionSignatureAssembler {
    @Override
    public String assetCode() {
        return "SOL";
    }

    @Override
    public SigningRequest prepare(UnsignedTransaction unsignedTransaction, CustodyKey custodyKey) {
        if (custodyKey.algorithm() != SigningAlgorithm.ED25519) {
            throw new WalletValidationException("SOL custody key must use ED25519 signing");
        }
        byte[] unsignedTransactionBytes = Base64.getDecoder().decode(unsignedTransaction.serializedPayload());
        byte[] message = extractMessage(unsignedTransactionBytes);
        return new SigningRequest(
            "SOL",
            unsignedTransaction.withdrawalId(),
            unsignedTransaction.serializedPayload(),
            CryptoEncoding.sha256Hex(message),
            Base64.getEncoder().encodeToString(message),
            custodyKey.keyAlias(),
            custodyKey.keyVersion(),
            custodyKey.algorithm(),
            "{\"format\":\"SOLANA_V0\"}"
        );
    }

    @Override
    public SignedTransactionDraft assemble(UnsignedTransaction unsignedTransaction, SigningResult signingResult, CustodyKey custodyKey) {
        byte[] signature = CryptoEncoding.decodeSignature(signingResult.signature());
        if (signature.length != 64) {
            throw new WalletValidationException("SOL custody signature must be 64 bytes");
        }
        byte[] unsignedTransactionBytes = Base64.getDecoder().decode(unsignedTransaction.serializedPayload());
        SignatureLayout layout = signatureLayout(unsignedTransactionBytes);
        byte[] signed = unsignedTransactionBytes.clone();
        System.arraycopy(signature, 0, signed, layout.signatureStart(), 64);
        return new SignedTransactionDraft(
            "SIGNED_SOLANA_V0",
            Base64.getEncoder().encodeToString(signed),
            CryptoEncoding.sha256Hex(extractMessage(unsignedTransactionBytes)),
            signingResult.signature(),
            custodyKey.algorithm()
        );
    }

    private static byte[] extractMessage(byte[] transaction) {
        SignatureLayout layout = signatureLayout(transaction);
        byte[] message = new byte[transaction.length - layout.messageStart()];
        System.arraycopy(transaction, layout.messageStart(), message, 0, message.length);
        return message;
    }

    private static SignatureLayout signatureLayout(byte[] transaction) {
        ShortVec count = readShortVec(transaction, 0);
        if (count.value() != 1) {
            throw new WalletValidationException("SOL unsigned transaction must contain one custody signature slot");
        }
        int signatureStart = count.nextOffset();
        int messageStart = signatureStart + 64;
        if (transaction.length <= messageStart) {
            throw new WalletValidationException("SOL unsigned transaction does not contain a message");
        }
        return new SignatureLayout(signatureStart, messageStart);
    }

    private static ShortVec readShortVec(byte[] bytes, int offset) {
        int value = 0;
        int shift = 0;
        int index = offset;
        while (index < bytes.length) {
            int current = bytes[index++] & 0xff;
            value |= (current & 0x7f) << shift;
            if ((current & 0x80) == 0) {
                return new ShortVec(value, index);
            }
            shift += 7;
            if (shift > 28) {
                break;
            }
        }
        throw new WalletValidationException("SOL transaction shortvec is invalid");
    }

    private record ShortVec(int value, int nextOffset) {}

    private record SignatureLayout(int signatureStart, int messageStart) {}
}
