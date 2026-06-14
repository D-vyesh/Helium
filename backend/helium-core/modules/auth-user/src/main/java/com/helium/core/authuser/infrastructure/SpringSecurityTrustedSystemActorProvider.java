package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.application.TrustedSystemActorAuthentication;
import com.helium.core.authuser.application.TrustedSystemActorProvider;
import com.helium.core.authuser.domain.AuthValidationException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityTrustedSystemActorProvider implements TrustedSystemActorProvider {
    @Override
    public void requireChainMonitor() {
        TrustedSystemActorAuthentication authentication = currentTrustedSystemActor()
            .orElseThrow(() -> new AuthValidationException("trusted chain monitor actor is required"));
        if (!authentication.isChainMonitor()) {
            throw new AuthValidationException("trusted chain monitor actor is required");
        }
    }

    @Override
    public String chainMonitorActorId() {
        requireChainMonitor();
        return TrustedSystemActorAuthentication.CHAIN_MONITOR_ACTOR_ID;
    }

    private Optional<TrustedSystemActorAuthentication> currentTrustedSystemActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication instanceof TrustedSystemActorAuthentication trustedSystemActor) {
            return Optional.of(trustedSystemActor);
        }
        return Optional.empty();
    }
}
