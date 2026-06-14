package com.helium.core.matching.application;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public final class TrustedMatchingAuthentication extends AbstractAuthenticationToken {
    public static final String MATCHING_ACTOR_ID = "system:matching-engine";

    private TrustedMatchingAuthentication() {
        super(AuthorityUtils.createAuthorityList("SYSTEM_MATCHING_ENGINE"));
        setAuthenticated(true);
    }

    static TrustedMatchingAuthentication trusted() {
        return new TrustedMatchingAuthentication();
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return MATCHING_ACTOR_ID;
    }

    @Override
    public String getName() {
        return MATCHING_ACTOR_ID;
    }
}
