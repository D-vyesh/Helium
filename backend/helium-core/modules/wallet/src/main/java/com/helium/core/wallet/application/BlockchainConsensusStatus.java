package com.helium.core.wallet.application;

public enum BlockchainConsensusStatus {
    AGREED,
    INSUFFICIENT_PROVIDERS,
    PROVIDER_DISAGREEMENT,
    TRANSACTION_NOT_FOUND,
    REORG_SUSPECTED
}
