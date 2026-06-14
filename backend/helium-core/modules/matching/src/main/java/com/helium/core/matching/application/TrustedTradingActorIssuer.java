package com.helium.core.matching.application;

import org.springframework.security.core.Authentication;

public interface TrustedTradingActorIssuer {
    Authentication issueTradingActor(String permission);
}
