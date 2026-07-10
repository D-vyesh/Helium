package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.SigningAlgorithm;
import java.time.Instant;
import java.util.UUID;

public record CustodySignatureAdminView(
    UUID withdrawalId,
    String assetCode,
    String networkCode,
    String format,
    String signingDigest,
    String custodyProvider,
    String keyAlias,
    String keyVersion,
    SigningAlgorithm algorithm,
    Instant signedAt
) {}
