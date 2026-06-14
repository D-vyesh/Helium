package com.helium.core.wallet.application;

import java.util.UUID;

public record ConfirmWithdrawalCommand(
    UUID withdrawalId
) {
}
