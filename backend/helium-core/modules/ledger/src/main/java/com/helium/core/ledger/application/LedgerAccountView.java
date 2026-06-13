package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import java.util.UUID;

public record LedgerAccountView(
    UUID accountId,
    LedgerAccountOwnerType ownerType,
    String ownerId,
    String assetCode,
    BalanceType balanceType
) {
}

