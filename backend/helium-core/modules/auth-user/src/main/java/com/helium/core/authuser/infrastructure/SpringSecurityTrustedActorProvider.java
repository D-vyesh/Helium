package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.application.TrustedActorProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityTrustedActorProvider implements TrustedActorProvider {
    @Override
    public String currentActorId() {
        return currentAuthentication().map(Authentication::getName).orElse("anonymous");
    }

    @Override
    public Optional<UUID> currentUserId() {
        return currentAuthentication().flatMap(authentication -> {
            try {
                return Optional.of(UUID.fromString(authentication.getName()));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    private Optional<Authentication> currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }
}
