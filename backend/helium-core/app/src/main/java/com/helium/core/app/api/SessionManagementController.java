package com.helium.core.app.api;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.SessionDetails;
import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping
    public List<SessionDetails> sessions(@RequestHeader(value = "X-Session-Token", required = false) String currentSessionToken) {
        return sessionPort.sessions(currentUserId(), currentSessionToken);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId, HttpServletRequest request) {
        sessionPort.revoke(currentUserId(), sessionId, ApiSecurity.context(request));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> revokeAllSessions(HttpServletRequest request) {
        UUID userId = currentUserId();
        sessionPort.revokeAll(userId, "user-initiated revocation", ApiSecurity.context(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/revoke/{targetUserId}")
    public ResponseEntity<Void> adminRevokeUserSessions(@PathVariable UUID targetUserId, HttpServletRequest request) {
        UUID adminId = currentUserId();
        authorizationPort.requireRole(adminId, Role.ADMIN);
        
        sessionPort.revokeAll(targetUserId, "admin-initiated revocation", ApiSecurity.context(request));
        return ResponseEntity.ok().build();
    }

    private UUID currentUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }
}
