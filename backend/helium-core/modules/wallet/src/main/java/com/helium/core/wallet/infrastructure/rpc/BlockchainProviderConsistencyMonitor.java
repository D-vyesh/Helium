package com.helium.core.wallet.infrastructure.rpc;

import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool.RpcProviderState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "helium.wallet.rpc.consistency-monitor.enabled", havingValue = "true", matchIfMissing = true)
public class BlockchainProviderConsistencyMonitor {
    private final BlockchainProviderPool providerPool;
    private final RpcProviderHealthPersistence healthPersistence;
    private final long allowedLag;
    private final Counter disagreements;

    public BlockchainProviderConsistencyMonitor(
        BlockchainProviderPool providerPool,
        RpcProviderHealthPersistence healthPersistence,
        MeterRegistry meterRegistry,
        @Value("${helium.wallet.rpc.consistency.allowed-block-lag:3}") long allowedLag
    ) {
        this.providerPool = providerPool;
        this.healthPersistence = healthPersistence;
        this.allowedLag = Math.max(1L, allowedLag);
        this.disagreements = Counter.builder("helium_wallet_rpc_provider_disagreements_total").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${helium.wallet.rpc.consistency-monitor.poll-interval-ms:30000}")
    public void checkProviderConsistency() {
        checkChain("BTC");
        checkChain("ETH");
        checkChain("SOL");
    }

    private void checkChain(String chain) {
        List<RpcProviderState> providers = providerPool.providers(chain).stream()
            .filter(provider -> provider.latestBlockHeight() != null)
            .toList();
        if (providers.size() < 2) {
            return;
        }
        long bestHeight = providers.stream()
            .mapToLong(provider -> provider.latestBlockHeight())
            .max()
            .orElse(0L);
        for (RpcProviderState provider : providers) {
            long lag = bestHeight - provider.latestBlockHeight();
            if (lag > allowedLag) {
                disagreements.increment();
                providerPool.markInconsistent(
                    provider,
                    "latest block height lags best provider by " + lag + " blocks"
                );
                healthPersistence.save(provider);
            }
        }
    }
}
