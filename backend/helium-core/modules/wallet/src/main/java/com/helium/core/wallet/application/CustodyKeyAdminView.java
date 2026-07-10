package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.CustodyKeyStatus;
import com.helium.core.wallet.domain.SigningAlgorithm;
import java.time.Instant;

public record CustodyKeyAdminView(
    String assetCode,
    String keyAlias,
    String keyVersion,
    String provider,
    SigningAlgorithm algorithm,
    CustodyKeyStatus status,
    boolean publicKeyAvailable,
    Instant createdAt,
    Instant activatedAt,
    Instant retiredAt
) {}
