package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import java.util.Objects;

public record CreateLedgerAccountCommand(
    LedgerAccountOwnerType ownerType,
    String ownerId,
    String assetCode,
    BalanceType balanceType,
    boolean negativeBalanceAllowed
) {
    public CreateLedgerAccountCommand(
        LedgerAccountOwnerType ownerType,
        String ownerId,
        String assetCode,
        BalanceType balanceType
    ) {
        this(ownerType, ownerId, assetCode, balanceType, false);
    }

    public CreateLedgerAccountCommand {
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(balanceType, "balanceType");
        ownerId = requireText(ownerId, "ownerId");
        assetCode = requireText(assetCode, "assetCode").toUpperCase();
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
