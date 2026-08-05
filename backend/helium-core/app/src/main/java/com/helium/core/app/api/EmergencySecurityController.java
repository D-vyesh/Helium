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
    private final ApiKeyService apiKeyService;
    private final EmergencySystemFreezeService emergencySystemFreezeService;

    public EmergencySecurityController(
        TrustedActorProvider trustedActorProvider,
        AuthorizationPort authorizationPort,
        ApplicationEventPublisher eventPublisher,
        ApiKeyService apiKeyService,
        EmergencySystemFreezeService emergencySystemFreezeService
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.authorizationPort = authorizationPort;
        this.eventPublisher = eventPublisher;
        this.apiKeyService = apiKeyService;
        this.emergencySystemFreezeService = emergencySystemFreezeService;
    }

    @PostMapping("/revoke-all-keys")
    public ResponseEntity<GlobalKeyRevocationResponse> revokeAllKeys() {
        requireAdmin();
        UUID actorId = currentUserId();
        int revoked = apiKeyService.revokeAll(actorId.toString());
        log.error("EMERGENCY: Administrator {} revoked {} active API keys", actorId, revoked);
        eventPublisher.publishEvent(new SecretRotationEvent(this, "GLOBAL_EMERGENCY", "v-any", "v-emergency"));
        return ResponseEntity.ok(new GlobalKeyRevocationResponse(revoked));
    }

    @PostMapping("/freeze-system")
    public ResponseEntity<SystemFreezeResponse> freezeSystem() {
        requireAdmin();
        UUID actorId = currentUserId();
        String reason = "Admin requested system freeze";
        EmergencySystemFreezeService.FreezeResult result = emergencySystemFreezeService.freeze(actorId, reason);
        log.error("EMERGENCY: Administrator {} froze {} markets, {} assets, and {} networks", actorId,
            result.haltedMarkets(), result.haltedAssets(), result.haltedNetworks());
        eventPublisher.publishEvent(new SystemFreezeEvent(this, actorId, reason));
        return ResponseEntity.ok(new SystemFreezeResponse(
            result.haltedMarkets(),
            result.haltedAssets(),
            result.haltedNetworks()
        ));
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
    public record GlobalKeyRevocationResponse(int revokedKeys) {}
    public record SystemFreezeResponse(int haltedMarkets, int haltedAssets, int haltedNetworks) {}
}
