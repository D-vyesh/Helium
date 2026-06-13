package com.helium.core.authuser.application;

import java.util.UUID;

public interface PasswordManagementPort {
    PasswordResetRequestResult requestReset(String email, SecurityContextData securityContext);

    void resetPassword(String rawToken, String newPassword, SecurityContextData securityContext);

    void changePassword(UUID userId, String currentPassword, String newPassword, SecurityContextData securityContext);
}
