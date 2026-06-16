package com.helium.core.app.api;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.domain.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security/emergency")
@Tag(name = "Admin - Emergency Security")
public class EmergencySecurityController {
    private static final Logger log = LoggerFactory.getLogger(EmergencySecurityController.class);

    private final TrustedActorProvider trustedActorProvider;
    private final AuthorizationPort authorizationPort;
    private final ApplicationEventPublisher eventPublisher;

    public EmergencySecurityController(
        TrustedActorProvider trustedActorProvider,
        AuthorizationPort authorizationPort,
        ApplicationEventPublisher eventPublisher
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.authorizationPort = authorizationPort;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/revoke-all-keys")
    public ResponseEntity<Void> revokeAllKeys() {
        requireAdmin();
        log.error("EMERGENCY: Administrator {} initiated a global revocation of all API keys", currentUserId());
        
        // This is a stub for the global key revocation logic
        // In a real system, this might trigger a Vault token revocation,
        // clear the Redis key cache entirely, and push a global kill switch event.
        
        // Triggering a generic rotation event to force all caches to clear.
        eventPublisher.publishEvent(new SecretRotationEvent(this, "GLOBAL_EMERGENCY", "v-any", "v-emergency"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/freeze-system")
    public ResponseEntity<Void> freezeSystem() {
        requireAdmin();
        log.error("EMERGENCY: Administrator {} initiated a global system freeze (trading & withdrawals)", currentUserId());
        
        // Emits a system freeze event which Matching and Settlement engines would listen to.
        eventPublisher.publishEvent(new SystemFreezeEvent(this, currentUserId(), "Admin requested system freeze"));
        return ResponseEntity.ok().build();
    }

    private void requireAdmin() {
        UUID userId = currentUserId();
        authorizationPort.requireRole(userId, Role.ADMIN);
    }

    private UUID currentUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }
    
    public record SystemFreezeEvent(Object source, UUID adminId, String reason) {}
}
