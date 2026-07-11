package com.helium.core.wallet.application;

import com.helium.core.wallet.infrastructure.rpc.RpcProviderHealthState;
import java.util.List;

public record BlockchainHealthAdminView(
    RpcProviderHealthState overallState,
    int healthyProviders,
    int degradedProviders,
    int unavailableProviders,
    List<BlockchainProviderAdminView> providers
) {
}
