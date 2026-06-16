package com.helium.core.wallet.infrastructure.blockchain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Registry holding all active blockchain providers.
 */
@Component
public class BlockchainProviderRegistry {

    private final Map<String, BlockchainProvider> providers;

    public BlockchainProviderRegistry(List<BlockchainProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(BlockchainProvider::networkId, Function.identity()));
    }

    public Optional<BlockchainProvider> getProvider(String networkId) {
        return Optional.ofNullable(providers.get(networkId));
    }

    public BlockchainProvider getRequiredProvider(String networkId) {
        return getProvider(networkId)
            .orElseThrow(() -> new IllegalArgumentException("No blockchain provider found for network: " + networkId));
    }
}
