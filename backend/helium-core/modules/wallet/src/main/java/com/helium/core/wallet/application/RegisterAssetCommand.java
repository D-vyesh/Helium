package com.helium.core.wallet.application;

public record RegisterAssetCommand(
    String assetCode,
    String name,
    int scale,
    boolean depositEnabled,
    boolean withdrawalEnabled
) {
}
