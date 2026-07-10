package com.helium.core.authuser.application;

import java.util.UUID;

public interface EmailVerificationPort {
    void issue(UUID userId, SecurityContextData securityContext);

    void issue(UUID userId, String baseUrl, SecurityContextData securityContext);

    void resend(String email, String baseUrl, SecurityContextData securityContext);

    void verify(String rawToken, SecurityContextData securityContext);
}
