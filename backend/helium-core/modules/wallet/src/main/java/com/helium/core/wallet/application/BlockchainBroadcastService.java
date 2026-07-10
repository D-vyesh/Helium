package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.SignedTransaction;

public interface BlockchainBroadcastService {
    String assetCode();

    BroadcastResult broadcast(SignedTransaction tx);
}
