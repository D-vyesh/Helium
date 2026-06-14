package com.helium.core.authuser.application;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public final class TrustedSystemActorAuthentication extends AbstractAuthenticationToken {
    public static final String CHAIN_MONITOR_ACTOR_ID = "system:chain-monitor";

    private static final String CHAIN_MONITOR_ACTOR_TYPE = "CHAIN_MONITOR";

    private final String actorId;
    private final String actorType;

    private TrustedSystemActorAuthentication(String actorId, String actorType) {
        super(AuthorityUtils.createAuthorityList("SYSTEM_" + actorType));
        this.actorId = actorId;
        this.actorType = actorType;
        setAuthenticated(true);
    }

    public static TrustedSystemActorAuthentication chainMonitor() {
        return new TrustedSystemActorAuthentication(CHAIN_MONITOR_ACTOR_ID, CHAIN_MONITOR_ACTOR_TYPE);
    }

    public boolean isChainMonitor() {
        return CHAIN_MONITOR_ACTOR_TYPE.equals(actorType);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return actorId;
    }

    @Override
    public String getName() {
        return actorId;
    }
}
