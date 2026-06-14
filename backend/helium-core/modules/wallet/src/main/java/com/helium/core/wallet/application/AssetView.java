package com.helium.core.wallet.application;

public record AssetView(
    String assetCode,
    int scale,
    boolean depositEnabled,
    boolean withdrawalEnabled
) {
}

