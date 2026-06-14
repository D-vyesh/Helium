package com.helium.core.matching.application;

import org.springframework.security.core.Authentication;

public interface TrustedMatchingActorIssuer {
    Authentication issueMatchingActor(String permission);
}
