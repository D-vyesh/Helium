package com.helium.core.authuser.application;

import java.util.UUID;

public interface EmailVerificationPort {
    String issue(UUID userId, SecurityContextData securityContext);

    void verify(String rawToken, SecurityContextData securityContext);
}
