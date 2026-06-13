package com.helium.core.ledger.application;

import java.util.UUID;

public interface BalanceQueryPort {
    BalanceSnapshotView getBalance(UUID accountId);
}

