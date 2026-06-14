package com.helium.core.matching.application;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public final class TrustedTradingAuthentication extends AbstractAuthenticationToken {
    public static final String TRADING_ACTOR_ID = "system:trading";

    private TrustedTradingAuthentication() {
        super(AuthorityUtils.createAuthorityList("SYSTEM_TRADING"));
        setAuthenticated(true);
    }

    static TrustedTradingAuthentication trusted() {
        return new TrustedTradingAuthentication();
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return TRADING_ACTOR_ID;
    }

    @Override
    public String getName() {
        return TRADING_ACTOR_ID;
    }
}
