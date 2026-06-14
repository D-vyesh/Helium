package com.helium.core.matching.infrastructure;

import com.helium.core.matching.application.TrustedTradingActorProvider;
import com.helium.core.matching.application.TrustedTradingAuthentication;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityTrustedTradingActorProvider implements TrustedTradingActorProvider {
    @Override
    public void requireTradingSystem() {
        currentTrustedTradingActor()
            .orElseThrow(() -> new IllegalStateException("trusted trading actor is required"));
    }

    @Override
    public String tradingActorId() {
        requireTradingSystem();
        return TrustedTradingAuthentication.TRADING_ACTOR_ID;
    }

    private Optional<TrustedTradingAuthentication> currentTrustedTradingActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication instanceof TrustedTradingAuthentication trustedTradingAuthentication
            && trustedTradingAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("SYSTEM_TRADING"::equals)) {
            return Optional.of(trustedTradingAuthentication);
        }
        return Optional.empty();
    }
}
