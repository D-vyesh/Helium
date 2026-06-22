package com.helium.core.app.api;

import com.helium.core.authuser.application.AuthenticationPort;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.LoginCommand;
import com.helium.core.authuser.application.LoginResult;
import com.helium.core.authuser.application.PasswordManagementPort;
import com.helium.core.authuser.application.RefreshTokenRotationResult;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.RoleManagementPort;
import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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
    private final ApiReadService readService;
    private final JwtTokenService jwtTokenService;

    public AuthApiController(
        RegistrationPort registrationPort,
        AuthenticationPort authenticationPort,
        EmailVerificationPort emailVerificationPort,
        PasswordManagementPort passwordManagementPort,
        SessionPort sessionPort,
        TrustedActorProvider trustedActorProvider,
        RoleManagementPort roleManagementPort,
        ApiReadService readService,
        JwtTokenService jwtTokenService
    ) {
        this.registrationPort = registrationPort;
        this.authenticationPort = authenticationPort;
        this.emailVerificationPort = emailVerificationPort;
        this.passwordManagementPort = passwordManagementPort;
        this.sessionPort = sessionPort;
        this.trustedActorProvider = trustedActorProvider;
        this.roleManagementPort = roleManagementPort;
        this.readService = readService;
        this.jwtTokenService = jwtTokenService;
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
            ApiSecurity.context(servletRequest)
        ));
        return new RegistrationResponse(result.userId(), true, result.emailVerificationToken());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        LoginResult result = authenticationPort.login(new LoginCommand(request.email(), request.password(), ApiSecurity.context(servletRequest)));
        if (!result.authenticated()) {
            throw new ApiUnauthorizedException("authentication failed");
        }
        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(result.userId(), result.roles());
        ResponseCookie cookie = ResponseCookie.from(ApiSecurity.SESSION_COOKIE, result.sessionToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.between(Instant.now(), result.expiresAt()).getSeconds())
            .build();
        Set<String> roles = roleNames(result.roles());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new LoginResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                result.sessionToken(),
                result.sessionToken(),
                result.expiresAt(),
                readService.user(result.userId(), roles),
                roles
            ));
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
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest logoutRequest, HttpServletRequest request) {
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
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    @PostMapping("/password-reset")
    public PasswordResetResponse passwordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        passwordManagementPort.requestReset(request.email(), ApiSecurity.context(servletRequest));
        return new PasswordResetResponse(true);
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

    public record RegistrationResponse(UUID userId, boolean emailVerificationRequired, String verificationToken) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

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

    public record PasswordResetRequest(@Email @NotBlank String email) {}

    public record PasswordResetResponse(boolean accepted) {}

    public record EmailVerificationRequest(@NotBlank String token) {}

    public record EmailVerificationResponse(boolean verified) {}
}
