package com.helium.core.wallet.application;

import java.util.UUID;

public record DepositAddressView(
    UUID depositAddressId,
    UUID userId,
    String assetCode,
    String networkCode,
    String address,
    String memo
) {
}

