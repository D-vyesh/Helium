package com.helium.core.wallet.application;

import java.util.UUID;

public record UpdateDepositConfirmationsCommand(
    UUID depositId,
    int confirmations
) {
}
