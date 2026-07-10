package com.helium.core.wallet.domain;

public enum WithdrawalConfirmationStatus {
    CONFIRMING,
    CONFIRMED,
    REORG_DETECTED,
    FAILED
}
