package com.helium.core.matching.infrastructure;

import com.helium.core.matching.application.TrustedMatchingActorProvider;
import com.helium.core.matching.application.TrustedMatchingAuthentication;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityTrustedMatchingActorProvider implements TrustedMatchingActorProvider {
    @Override
    public void requireMatchingEngine() {
        currentTrustedMatchingActor()
            .orElseThrow(() -> new IllegalStateException("trusted matching actor is required"));
    }

    @Override
    public String matchingActorId() {
        requireMatchingEngine();
        return TrustedMatchingAuthentication.MATCHING_ACTOR_ID;
    }

    private Optional<TrustedMatchingAuthentication> currentTrustedMatchingActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication instanceof TrustedMatchingAuthentication trustedMatchingAuthentication
            && trustedMatchingAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("SYSTEM_MATCHING_ENGINE"::equals)) {
            return Optional.of(trustedMatchingAuthentication);
        }
        return Optional.empty();
    }
}
