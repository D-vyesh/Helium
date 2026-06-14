package com.helium.core.wallet.application;

public interface AssetPort {
    AssetView registerAsset(RegisterAssetCommand command);

    NetworkView registerNetwork(RegisterNetworkCommand command);
}

