package com.helium.core.wallet.domain;

public enum DepositStatus {
    DETECTED,
    PENDING_CONFIRMATIONS,
    CONFIRMED,
    POSTED_TO_LEDGER,
    CHAIN_REVIEW_REQUIRED,
    FAILED,
    REORGED
}
