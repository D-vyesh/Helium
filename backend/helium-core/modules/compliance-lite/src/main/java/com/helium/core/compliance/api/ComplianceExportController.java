package com.helium.core.compliance.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/compliance/data")
@Tag(name = "Compliance - Data Rights")
public class ComplianceExportController {
    private static final Logger log = LoggerFactory.getLogger(ComplianceExportController.class);

    private final TrustedActorProvider trustedActorProvider;

    public ComplianceExportController(TrustedActorProvider trustedActorProvider) {
        this.trustedActorProvider = trustedActorProvider;
    }

    /**
     * GDPR/CCPA Data Export Request
     */
    @GetMapping("/export")
    public ResponseEntity<String> requestDataExport() {
        UUID userId = currentUserId();
        log.warn("User {} requested GDPR data export, but no export workflow is configured", userId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Data export is unavailable until a retention-aware export workflow is configured.");
    }

    /**
     * GDPR/CCPA Right to be Forgotten Request
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> requestAccountDeletion() {
        UUID userId = currentUserId();
        log.warn("User {} requested account deletion, but no retention-aware deletion workflow is configured", userId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Account deletion is unavailable until a retention-aware workflow is configured.");
    }

    private UUID currentUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated session is required"));
    }
}
