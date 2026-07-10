package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.SigningAlgorithm;
import java.util.Objects;
import java.util.UUID;

public record SigningRequest(
    String assetCode,
    UUID withdrawalId,
    String unsignedTransaction,
    String digestHex,
    String payloadBase64,
    String keyAlias,
    String keyVersion,
    SigningAlgorithm signingAlgorithm,
    String metadata
) {
    public SigningRequest {
        assetCode = BlockchainNetwork.requireText(assetCode, "assetCode", 32).toUpperCase();
        Objects.requireNonNull(withdrawalId, "withdrawalId");
        unsignedTransaction = BlockchainNetwork.requireText(unsignedTransaction, "unsignedTransaction", 2_000_000);
        digestHex = BlockchainNetwork.requireText(digestHex, "digestHex", 160);
        payloadBase64 = BlockchainNetwork.requireText(payloadBase64, "payloadBase64", 2_000_000);
        keyAlias = BlockchainNetwork.requireText(keyAlias, "keyAlias", 160);
        keyVersion = BlockchainNetwork.requireText(keyVersion, "keyVersion", 80);
        Objects.requireNonNull(signingAlgorithm, "signingAlgorithm");
        metadata = metadata == null || metadata.isBlank() ? "{}" : BlockchainNetwork.requireText(metadata, "metadata", 2_000_000);
    }
}
