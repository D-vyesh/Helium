package com.helium.core.authuser.application;

import java.util.Optional;
import java.util.UUID;

public interface SessionPort {
    Optional<SessionView> validate(String rawToken);

    void logout(String rawToken, SecurityContextData securityContext);

    void revokeAll(UUID userId, String reason, SecurityContextData securityContext);
}
