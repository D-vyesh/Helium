package com.helium.core.wallet.application;

public record UpdateChainMonitorCommand(
    String networkCode,
    long blockHeight
) {
}
