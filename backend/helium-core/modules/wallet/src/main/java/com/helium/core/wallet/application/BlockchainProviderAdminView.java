package com.helium.core.wallet.application;

import com.helium.core.wallet.infrastructure.rpc.RpcCircuitState;
import com.helium.core.wallet.infrastructure.rpc.RpcProviderHealthState;
import java.time.Instant;

public record BlockchainProviderAdminView(
    String id,
    String chain,
    String url,
    boolean manuallyDisabled,
    RpcProviderHealthState healthState,
    RpcCircuitState circuitState,
    int consecutiveFailures,
    long requests,
    long failures,
    long timeouts,
    long lastLatencyMs,
    Long latestBlockHeight,
    Instant lastSuccessAt,
    Instant lastFailureAt,
    Instant retryAfterAt,
    String lastConsistencyIssue,
    Instant disabledAt
) {
}
