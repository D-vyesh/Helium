package com.helium.core.wallet.domain;

public enum DepositStatus {
    DETECTED,
    PENDING_CONFIRMATIONS,
    CONFIRMED,
    POSTED_TO_LEDGER,
    FAILED,
    REORGED
}
