package com.helium.core.matching.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredTrustedTradingActorIssuer implements TrustedTradingActorIssuer {
    private final String tradingPermission;

    public ConfiguredTrustedTradingActorIssuer(
        @Value("${helium.trading.actor-permission:local-dev-trading-permission}") String tradingPermission
    ) {
        this.tradingPermission = tradingPermission;
    }

    @Override
    public Authentication issueTradingActor(String permission) {
        if (!tradingPermission.equals(permission)) {
            throw new IllegalArgumentException("trading actor permission is invalid");
        }
        return TrustedTradingAuthentication.trusted();
    }
}
