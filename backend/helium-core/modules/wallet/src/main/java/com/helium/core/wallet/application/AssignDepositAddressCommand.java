package com.helium.core.wallet.application;

public record AssignDepositAddressCommand(
    String assetCode,
    String networkCode,
    String address,
    String memo
) {
}
