package com.helium.core.app.api;

import com.helium.core.app.api.email.EmailProperties;
import com.helium.core.authuser.application.AuthenticationPort;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.LoginCommand;
import com.helium.core.authuser.application.LoginFailureReason;
import com.helium.core.authuser.application.LoginResult;
import com.helium.core.authuser.application.PasswordManagementPort;
import com.helium.core.authuser.application.RefreshTokenRotationResult;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.RoleManagementPort;
import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.application.TotpConfirmResult;
import com.helium.core.authuser.application.TotpPort;
import com.helium.core.authuser.application.TotpSetupResult;
import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthApiController {
    private final RegistrationPort registrationPort;
    private final AuthenticationPort authenticationPort;
    private final EmailVerificationPort emailVerificationPort;
    private final PasswordManagementPort passwordManagementPort;
    private final SessionPort sessionPort;
    private final TrustedActorProvider trustedActorProvider;
    private final RoleManagementPort roleManagementPort;
    private final TotpPort totpPort;
    private final ApiReadService readService;
    private final JwtTokenService jwtTokenService;
    private final EmailProperties emailProperties;

    public AuthApiController(
        RegistrationPort registrationPort,
        AuthenticationPort authenticationPort,
        EmailVerificationPort emailVerificationPort,
        PasswordManagementPort passwordManagementPort,
        SessionPort sessionPort,
        TrustedActorProvider trustedActorProvider,
        RoleManagementPort roleManagementPort,
        TotpPort totpPort,
        ApiReadService readService,
        JwtTokenService jwtTokenService,
        EmailProperties emailProperties
    ) {
        this.registrationPort = registrationPort;
        this.authenticationPort = authenticationPort;
        this.emailVerificationPort = emailVerificationPort;
        this.passwordManagementPort = passwordManagementPort;
        this.sessionPort = sessionPort;
        this.trustedActorProvider = trustedActorProvider;
        this.roleManagementPort = roleManagementPort;
        this.totpPort = totpPort;
        this.readService = readService;
        this.jwtTokenService = jwtTokenService;
        this.emailProperties = emailProperties;
    }

    @PostMapping("/signup")
    public RegistrationResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("password confirmation does not match");
        }
        return register(request.email(), displayNameFrom(request.email()), request.password(), servletRequest);
    }

    @PostMapping("/register")
    public RegistrationResponse registerLegacy(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return register(request.email(), request.displayName(), request.password(), servletRequest);
    }

    private RegistrationResponse register(String email, String displayName, String password, HttpServletRequest servletRequest) {
        RegistrationResult result = registrationPort.register(new RegistrationCommand(
            email,
            displayName,
            password,
            ApiSecurity.context(servletRequest),
            emailProperties.baseUrl()
        ));
        // Token is NEVER returned — it is sent via email
        return new RegistrationResponse(result.userId(), true);
    }

    @PostMapping("/login")
    public Object login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        LoginResult result = authenticationPort.login(new LoginCommand(request.email(), request.password(), ApiSecurity.context(servletRequest)));
        if (!result.authenticated()) {
            if (result.failureReason() == LoginFailureReason.MFA_REQUIRED) {
                // Return MFA challenge — client must POST to /auth/mfa/totp/challenge
                return new MfaChallengeResponse(result.mfaSessionToken());
            }
            throw new ApiUnauthorizedException("authentication failed");
        }
        return buildLoginResponse(result, servletResponse);
    }

    private LoginResponse buildLoginResponse(LoginResult result, HttpServletResponse servletResponse) {
        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(result.userId(), result.roles());
        ResponseCookie cookie = ResponseCookie.from(ApiSecurity.SESSION_COOKIE, result.sessionToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.between(Instant.now(), result.expiresAt()).getSeconds())
            .build();
        Set<String> roles = roleNames(result.roles());
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new LoginResponse(
            accessToken.token(),
            accessToken.expiresAt(),
            result.sessionToken(),
            result.sessionToken(),
            result.expiresAt(),
            readService.user(result.userId(), roles),
            roles
        );
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        RefreshTokenRotationResult rotation = sessionPort.rotate(request.refreshToken(), ApiSecurity.context(servletRequest));
        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(rotation.userId(), rotation.roles());
        return new TokenResponse(
            accessToken.token(),
            accessToken.expiresAt(),
            rotation.refreshToken(),
            rotation.refreshTokenExpiresAt(),
            roleNames(rotation.roles())
        );
    }

    @PostMapping("/logout")
    public void logout(
        @Valid @RequestBody LogoutRequest logoutRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (logoutRequest.allSessions()) {
            sessionPort.logoutAll(logoutRequest.refreshToken(), ApiSecurity.context(request));
        } else {
            sessionPort.logout(logoutRequest.refreshToken(), ApiSecurity.context(request));
        }
        ResponseCookie expired = ResponseCookie.from(ApiSecurity.SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @PostMapping("/password-reset/request")
    public PasswordResetResponse passwordResetRequest(@Valid @RequestBody PasswordResetRequestBody request, HttpServletRequest servletRequest) {
        passwordManagementPort.requestReset(request.email(), emailProperties.baseUrl(), ApiSecurity.context(servletRequest));
        return new PasswordResetResponse(true);
    }

    /** Legacy endpoint kept for backward compatibility */
    @PostMapping("/password-reset")
    public PasswordResetResponse passwordReset(@Valid @RequestBody PasswordResetRequestBody request, HttpServletRequest servletRequest) {
        return passwordResetRequest(request, servletRequest);
    }

    @PostMapping("/password-reset/confirm")
    public PasswordResetConfirmResponse passwordResetConfirm(@Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest servletRequest) {
        passwordManagementPort.resetPassword(request.token(), request.newPassword(), ApiSecurity.context(servletRequest));
        return new PasswordResetConfirmResponse(true);
    }

    @PostMapping("/password/change")
    public PasswordResetConfirmResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest servletRequest) {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        passwordManagementPort.changePassword(userId, request.currentPassword(), request.newPassword(), ApiSecurity.context(servletRequest));
        return new PasswordResetConfirmResponse(true);
    }

    @PostMapping("/email-verification/resend")
    public EmailVerificationResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest servletRequest) {
        UUID userId = trustedActorProvider.currentUserId().orElse(null);
        if (userId != null) {
            emailVerificationPort.issue(userId, emailProperties.baseUrl(), ApiSecurity.context(servletRequest));
        } else if (request.email() != null && !request.email().isBlank()) {
            emailVerificationPort.resend(request.email(), emailProperties.baseUrl(), ApiSecurity.context(servletRequest));
        }
        return new EmailVerificationResponse(true);
    }

    // ---- TOTP MFA endpoints ----

    @PostMapping("/mfa/totp/setup")
    public TotpSetupResponse totpSetup(HttpServletRequest servletRequest) {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        TotpSetupResult result = totpPort.beginSetup(userId, ApiSecurity.context(servletRequest));
        return new TotpSetupResponse(result.secret(), result.otpAuthUrl(), result.qrCodeDataUrl());
    }

    @PostMapping("/mfa/totp/confirm")
    public TotpConfirmResponse totpConfirm(@Valid @RequestBody TotpCodeRequest request, HttpServletRequest servletRequest) {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        TotpConfirmResult result = totpPort.confirmSetup(userId, request.code(), ApiSecurity.context(servletRequest));
        return new TotpConfirmResponse(result.enabled(), result.backupCodes());
    }

    @DeleteMapping("/mfa/totp")
    public TotpDisableResponse totpDisable(@Valid @RequestBody TotpCodeRequest request, HttpServletRequest servletRequest) {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        totpPort.disable(userId, request.code(), ApiSecurity.context(servletRequest));
        return new TotpDisableResponse(true);
    }

    @PostMapping("/mfa/totp/challenge")
    public LoginResponse totpChallenge(
        @Valid @RequestBody TotpChallengeRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        LoginResult result = totpPort.completeChallenge(request.mfaSessionToken(), request.code(), ApiSecurity.context(servletRequest));
        if (!result.authenticated()) {
            throw new ApiUnauthorizedException("TOTP verification failed");
        }
        return buildLoginResponse(result, servletResponse);
    }

    @PostMapping("/mfa/totp/backup")
    public LoginResponse totpBackupCode(
        @Valid @RequestBody BackupCodeRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        LoginResult result = totpPort.completeWithBackupCode(request.mfaSessionToken(), request.backupCode(), ApiSecurity.context(servletRequest));
        if (!result.authenticated()) {
            throw new ApiUnauthorizedException("backup code verification failed");
        }
        return buildLoginResponse(result, servletResponse);
    }

    @GetMapping("/mfa/totp/backup-codes")
    public BackupCodesResponse listBackupCodes() {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        return new BackupCodesResponse(totpPort.listBackupCodes(userId));
    }

    @PostMapping("/mfa/totp/backup-codes/regenerate")
    public BackupCodesResponse regenerateBackupCodes(@Valid @RequestBody TotpCodeRequest request, HttpServletRequest servletRequest) {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        List<String> codes = totpPort.regenerateBackupCodes(userId, request.code(), ApiSecurity.context(servletRequest));
        return new BackupCodesResponse(codes);
    }

    @GetMapping("/verify")
    public EmailVerificationResponse verifyEmailByQuery(
        @RequestParam("token") String token,
        HttpServletRequest servletRequest
    ) {
        emailVerificationPort.verify(token, ApiSecurity.context(servletRequest));
        return new EmailVerificationResponse(true);
    }

    @PostMapping("/email-verification")
    public EmailVerificationResponse verifyEmail(
        @Valid @RequestBody EmailVerificationRequest request,
        HttpServletRequest servletRequest
    ) {
        emailVerificationPort.verify(request.token(), ApiSecurity.context(servletRequest));
        return new EmailVerificationResponse(true);
    }

    @GetMapping("/session")
    public ApiReadService.UserDto session() {
        UUID userId = trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        return readService.user(userId, roleNames(roleManagementPort.rolesFor(userId)));
    }

    private Set<String> roleNames(Set<com.helium.core.authuser.domain.Role> roles) {
        return roles.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private String displayNameFrom(String email) {
        String trimmed = email == null ? "User" : email.trim();
        int at = trimmed.indexOf('@');
        String localPart = at > 0 ? trimmed.substring(0, at) : trimmed;
        return localPart.isBlank() ? "User" : localPart;
    }

    public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 12, max = 200) String password,
        @NotBlank @Size(min = 12, max = 200) String confirmPassword
    ) {}

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 12, max = 200) String password
    ) {}

    /** Token is NEVER included — it is sent via email only. */
    public record RegistrationResponse(UUID userId, boolean emailVerificationRequired) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record MfaChallengeResponse(String mfaSessionToken) {}

    public record LoginResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        String sessionToken,
        Instant refreshTokenExpiresAt,
        ApiReadService.UserDto user,
        Set<String> roles
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        Set<String> roles
    ) {}

    public record LogoutRequest(@NotBlank String refreshToken, boolean allSessions) {}

    public record PasswordResetRequestBody(@Email @NotBlank String email) {}

    public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 200) String newPassword
    ) {}

    public record PasswordResetResponse(boolean accepted) {}

    public record PasswordResetConfirmResponse(boolean success) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 12, max = 200) String newPassword
    ) {}

    public record EmailVerificationRequest(@NotBlank String token) {}

    public record EmailVerificationResponse(boolean verified) {}

    public record ResendVerificationRequest(@Email String email) {}

    // TOTP records
    public record TotpSetupResponse(String secret, String otpAuthUrl, String qrCodeDataUrl) {}

    public record TotpConfirmResponse(boolean enabled, List<String> backupCodes) {}

    public record TotpDisableResponse(boolean disabled) {}

    public record TotpCodeRequest(@NotBlank String code) {}

    public record TotpChallengeRequest(@NotBlank String mfaSessionToken, @NotBlank String code) {}

    public record BackupCodeRequest(@NotBlank String mfaSessionToken, @NotBlank String backupCode) {}

    public record BackupCodesResponse(List<String> codes) {}
}
