package com.helium.core.wallet.application;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalAuthorizationView(
    UUID withdrawalId,
    boolean emailConfirmed,
    boolean mfaConfirmed,
    Instant emailExpiresAt
) {}
