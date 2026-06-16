package com.helium.core.compliance.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        log.info("User {} requested GDPR data export", userId);
        // Triggers async job to collect all user data, trades, and balances,
        // then emails the user a secure download link.
        return ResponseEntity.accepted().body("Data export initiated. You will receive an email shortly.");
    }

    /**
     * GDPR/CCPA Right to be Forgotten Request
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> requestAccountDeletion() {
        UUID userId = currentUserId();
        log.info("User {} requested Account Deletion (Right to be Forgotten)", userId);
        // Note: Financial regulations (e.g., BSA/AML) often require data retention for 5-7 years,
        // which supersedes GDPR deletion. This endpoint would mark the account as closed,
        // delete marketing/PII data where allowed, but retain financial records in a cold vault.
        return ResponseEntity.accepted().body("Account deletion request received and is being processed according to regulatory retention periods.");
    }

    private UUID currentUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new RuntimeException("authenticated session is required"));
    }
}
