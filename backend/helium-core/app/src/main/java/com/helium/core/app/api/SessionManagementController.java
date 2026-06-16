package com.helium.core.app.api;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.domain.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions")
public class SessionManagementController {

    private final SessionPort sessionPort;
    private final TrustedActorProvider trustedActorProvider;
    private final AuthorizationPort authorizationPort;

    public SessionManagementController(
        SessionPort sessionPort,
        TrustedActorProvider trustedActorProvider,
        AuthorizationPort authorizationPort
    ) {
        this.sessionPort = sessionPort;
        this.trustedActorProvider = trustedActorProvider;
        this.authorizationPort = authorizationPort;
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> revokeAllSessions() {
        UUID userId = currentUserId();
        // Revoke all sessions for the current user
        sessionPort.revokeAll(userId, "user-initiated revocation", null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/revoke/{targetUserId}")
    public ResponseEntity<Void> adminRevokeUserSessions(@PathVariable UUID targetUserId) {
        UUID adminId = currentUserId();
        authorizationPort.requireRole(adminId, Role.ADMIN);
        
        sessionPort.revokeAll(targetUserId, "admin-initiated revocation", null);
        return ResponseEntity.ok().build();
    }

    private UUID currentUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }
}
