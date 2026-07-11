package com.helium.core.wallet.application;

import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool;
import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool.RpcProviderState;
import com.helium.core.wallet.infrastructure.rpc.RpcProviderHealthPersistence;
import com.helium.core.wallet.infrastructure.rpc.RpcProviderHealthState;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BlockchainProviderAdminService {
    private final BlockchainProviderPool providerPool;
    private final RpcProviderHealthPersistence healthPersistence;

    public BlockchainProviderAdminService(
        BlockchainProviderPool providerPool,
        RpcProviderHealthPersistence healthPersistence
    ) {
        this.providerPool = providerPool;
        this.healthPersistence = healthPersistence;
    }

    public List<BlockchainProviderAdminView> providers() {
        return providerPool.providers().stream().map(this::toView).toList();
    }

    public List<BlockchainProviderAdminView> providers(String chain) {
        return providerPool.providers(chain).stream().map(this::toView).toList();
    }

    public BlockchainHealthAdminView health() {
        List<BlockchainProviderAdminView> views = providers();
        int healthy = (int) views.stream().filter(provider -> provider.healthState() == RpcProviderHealthState.HEALTHY).count();
        int degraded = (int) views.stream().filter(provider -> provider.healthState() == RpcProviderHealthState.DEGRADED).count();
        int unavailable = (int) views.stream().filter(provider -> provider.healthState() == RpcProviderHealthState.UNAVAILABLE).count();
        RpcProviderHealthState overall = healthy > 0
            ? (degraded > 0 || unavailable > 0 ? RpcProviderHealthState.DEGRADED : RpcProviderHealthState.HEALTHY)
            : RpcProviderHealthState.UNAVAILABLE;
        return new BlockchainHealthAdminView(overall, healthy, degraded, unavailable, views);
    }

    public void disable(String providerId) {
        providerPool.disable(providerId);
        providerPool.providers().stream()
            .filter(provider -> provider.id().equals(providerId))
            .findFirst()
            .ifPresent(healthPersistence::save);
    }

    public void enable(String providerId) {
        providerPool.enable(providerId);
        providerPool.providers().stream()
            .filter(provider -> provider.id().equals(providerId))
            .findFirst()
            .ifPresent(healthPersistence::save);
    }

    private BlockchainProviderAdminView toView(RpcProviderState provider) {
        return new BlockchainProviderAdminView(
            provider.id(),
            provider.chain(),
            redactedUrl(provider.url()),
            provider.manuallyDisabled(),
            provider.healthState(),
            provider.circuitState(),
            provider.consecutiveFailures(),
            provider.requestCount(),
            provider.failureCount(),
            provider.timeoutCount(),
            provider.lastLatency().toMillis(),
            provider.latestBlockHeight(),
            provider.lastSuccessAt(),
            provider.lastFailureAt(),
            provider.retryAfterAt(),
            provider.lastConsistencyIssue(),
            provider.disabledAt()
        );
    }

    private static String redactedUrl(String url) {
        if (url == null) {
            return null;
        }
        int at = url.indexOf('@');
        if (at < 0) {
            return url;
        }
        int scheme = url.indexOf("://");
        if (scheme < 0 || scheme > at) {
            return url.substring(at + 1);
        }
        return url.substring(0, scheme + 3) + "redacted@" + url.substring(at + 1);
    }
}
