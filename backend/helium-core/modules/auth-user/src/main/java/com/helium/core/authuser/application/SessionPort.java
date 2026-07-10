package com.helium.core.authuser.application;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface SessionPort {
    Optional<SessionView> validate(String rawToken);

    RefreshTokenRotationResult rotate(String rawToken, SecurityContextData securityContext);

    void logout(String rawToken, SecurityContextData securityContext);

    void logoutAll(String rawToken, SecurityContextData securityContext);

    void revokeAll(UUID userId, String reason, SecurityContextData securityContext);

    List<SessionDetails> sessions(UUID userId, String currentSessionToken);

    void revoke(UUID userId, UUID sessionId, SecurityContextData securityContext);
}
