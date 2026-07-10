package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CustodyProviderRouter {
    private final Map<String, CustodyProvider> providers;

    public CustodyProviderRouter(List<CustodyProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
            provider -> normalize(provider.providerName()),
            Function.identity(),
            (left, right) -> { throw new IllegalStateException("duplicate custody provider name"); }
        ));
    }

    public CustodyProvider requiredProvider(String providerName) {
        CustodyProvider provider = providers.get(normalize(providerName));
        if (provider == null) {
            throw new WalletValidationException("custody provider is not configured: " + providerName);
        }
        if (!provider.isHealthy()) {
            throw new WalletValidationException("custody provider is not healthy: " + providerName);
        }
        return provider;
    }

    private static String normalize(String providerName) {
        return providerName == null ? "" : providerName.trim().toUpperCase(Locale.ROOT);
    }
}
