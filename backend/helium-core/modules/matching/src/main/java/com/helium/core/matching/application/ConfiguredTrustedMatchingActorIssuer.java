package com.helium.core.matching.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredTrustedMatchingActorIssuer implements TrustedMatchingActorIssuer {
    private final String matchingPermission;

    public ConfiguredTrustedMatchingActorIssuer(
        @Value("${helium.matching.actor-permission:local-dev-matching-permission}") String matchingPermission
    ) {
        this.matchingPermission = matchingPermission;
    }

    @Override
    public Authentication issueMatchingActor(String permission) {
        if (!matchingPermission.equals(permission)) {
            throw new IllegalArgumentException("matching actor permission is invalid");
        }
        return TrustedMatchingAuthentication.trusted();
    }
}
