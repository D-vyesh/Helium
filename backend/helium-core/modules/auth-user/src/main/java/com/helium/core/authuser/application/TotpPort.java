package com.helium.core.authuser.application;

import java.util.List;
import java.util.UUID;

/**
 * Application port for TOTP MFA lifecycle operations.
 */
public interface TotpPort {

    /** Begin TOTP setup: generate secret, persist pending MFA method, return setup data. */
    TotpSetupResult beginSetup(UUID userId, SecurityContextData securityContext);

    /** Confirm TOTP setup by verifying the first code. Activates the MFA method. */
    TotpConfirmResult confirmSetup(UUID userId, String totpCode, SecurityContextData securityContext);

    /** Disable TOTP for the user (requires current TOTP code or backup code). */
    void disable(UUID userId, String totpCode, SecurityContextData securityContext);

    /** Complete a login that requires TOTP. Returns a full session on success. */
    LoginResult completeChallenge(String mfaSessionToken, String totpCode, SecurityContextData securityContext);

    /** Verify a backup code and complete login. */
    LoginResult completeWithBackupCode(String mfaSessionToken, String backupCode, SecurityContextData securityContext);

    /** List remaining backup codes for the authenticated user. */
    List<String> listBackupCodes(UUID userId);

    /** Regenerate backup codes (invalidates old ones). */
    List<String> regenerateBackupCodes(UUID userId, String totpCode, SecurityContextData securityContext);

    /** Verify an enabled TOTP factor before a high-risk account action. */
    void verifySensitiveAction(UUID userId, String totpCode, SecurityContextData securityContext);
}
