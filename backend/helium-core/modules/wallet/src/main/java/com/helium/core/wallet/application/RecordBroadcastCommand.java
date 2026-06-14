package com.helium.core.wallet.application;

import java.util.UUID;

public record RecordBroadcastCommand(
    UUID withdrawalId,
    String txHash
) {
}
