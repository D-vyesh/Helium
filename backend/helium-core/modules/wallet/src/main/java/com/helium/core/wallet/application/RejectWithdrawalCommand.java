package com.helium.core.wallet.application;

import java.util.UUID;

public record RejectWithdrawalCommand(
    UUID withdrawalId,
    String reason
) {
}
