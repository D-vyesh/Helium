package com.helium.core.authuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.authuser.application.AccountAdministrationPort;
import com.helium.core.authuser.application.AuthenticationPort;
import com.helium.core.authuser.application.AuthorizationPort;
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
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class)
@Testcontainers
class AuthUserPostgresIntegrationTest {
    private static final String INITIAL_PASSWORD = "Initial-password-123";
    private static final SecurityContextData CONTEXT = new SecurityContextData("127.0.0.1", "integration-test");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegistrationPort registrationPort;

    @Autowired
    private EmailVerificationPort emailVerificationPort;

    @Autowired
    private AuthenticationPort authenticationPort;

    @Autowired
    private PasswordManagementPort passwordManagementPort;

    @Autowired
    private SessionPort sessionPort;

    @Autowired
    private RoleManagementPort roleManagementPort;

    @Autowired
    private AuthorizationPort authorizationPort;

    @Autowired
    private AccountAdministrationPort accountAdministrationPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearAuthUserData() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.execute("""
            truncate table
                auth_security_audit_events,
                login_attempts,
                auth_login_attempt_throttles,
                auth_mfa_methods,
                auth_password_reset_tokens,
                auth_email_verification_tokens,
                auth_role_grants,
                auth_user_sessions,
                auth_credentials,
                auth_user_accounts
            cascade
            """);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signupSuccessStoresBCryptAndHashedVerificationTokenThenVerifiesEmail() {
        RegistrationResult result = register("User@Example.com");

        Map<String, Object> credential = jdbcTemplate.queryForMap(
            "select password_hash from auth_credentials where user_id = ?",
            result.userId()
        );
        String storedVerificationHash = jdbcTemplate.queryForObject(
            "select token_hash from auth_email_verification_tokens where user_id = ?",
            String.class,
            result.userId()
        );

        assertThat(credential.get("password_hash").toString()).startsWith("$2").doesNotContain(INITIAL_PASSWORD);
        assertThat(storedVerificationHash).hasSize(64).isNotEqualTo(result.emailVerificationToken());
        assertThat(statusOf(result.userId())).isEqualTo("EMAIL_UNVERIFIED");

        emailVerificationPort.verify(result.emailVerificationToken(), CONTEXT);

        assertThat(statusOf(result.userId())).isEqualTo("ACTIVE");
        assertThat(roleManagementPort.rolesFor(result.userId())).containsExactly(Role.USER);
    }

    @Test
    void authenticatesCreatesSessionAndLogsOut() {
        RegistrationResult user = registerAndVerify("user@example.com");

        LoginResult login = login("user@example.com", INITIAL_PASSWORD);

        assertThat(login.authenticated()).isTrue();
        assertThat(login.roles()).containsExactly(Role.USER);
        assertThat(sessionPort.validate(login.sessionToken())).isPresent();

        sessionPort.logout(login.sessionToken(), CONTEXT);

        assertThat(sessionPort.validate(login.sessionToken())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select status from auth_user_sessions where id = ?",
            String.class,
            sessionIdFor(user.userId())
        )).isEqualTo("REVOKED");
    }

    @Test
    void locksAccountAfterFiveFailedLogins() {
        RegistrationResult user = registerAndVerify("locked@example.com");
        LoginResult existingLogin = login("locked@example.com", INITIAL_PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(loginFromIp("locked@example.com", "Wrong-password-123", "192.0.2." + attempt).authenticated()).isFalse();
        }

        assertThat(statusOf(user.userId())).isEqualTo("LOCKED");
        assertThat(sessionPort.validate(existingLogin.sessionToken())).isEmpty();
        LoginResult locked = login("locked@example.com", INITIAL_PASSWORD);
        assertThat(locked.failureReason()).isEqualTo(LoginFailureReason.AUTHENTICATION_FAILED);
    }

    @Test
    void recordsAnonymousLoginFailuresWithoutCreatingUsers() {
        for (int attempt = 0; attempt < 6; attempt++) {
            LoginResult result = login("missing@example.com", "Wrong-password-123");
            assertThat(result.authenticated()).isFalse();
            assertThat(result.failureReason()).isEqualTo(LoginFailureReason.AUTHENTICATION_FAILED);
        }

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from login_attempts where email = 'missing@example.com' and success = false",
            Integer.class
        )).isEqualTo(6);
    }

    @Test
    void rejectsWeakPasswords() {
        assertThatThrownBy(() -> registrationPort.register(new RegistrationCommand(
            "weak@example.com",
            "Weak User",
            "lowercase1234",
            CONTEXT
        )))
            .isInstanceOf(AuthValidationException.class)
            .hasMessageContaining("uppercase");
    }

    @Test
    void refreshTokenRotationRevokesPreviousTokenAndIssuesReplacement() {
        registerAndVerify("refresh@example.com");
        LoginResult login = login("refresh@example.com", INITIAL_PASSWORD);

        RefreshTokenRotationResult rotation = sessionPort.rotate(login.sessionToken(), CONTEXT);

        assertThat(rotation.refreshToken()).isNotEqualTo(login.sessionToken());
        assertThat(sessionPort.validate(login.sessionToken())).isEmpty();
        assertThat(sessionPort.validate(rotation.refreshToken())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from auth_user_sessions where user_id = ? and status = 'ACTIVE'",
            Integer.class,
            rotation.userId()
        )).isOne();
    }

    @Test
    void passwordResetChangesCredentialAndRevokesExistingSessions() {
        RegistrationResult user = registerAndVerify("reset@example.com");
        LoginResult existingLogin = login("reset@example.com", INITIAL_PASSWORD);
        String resetToken = passwordManagementPort.requestReset("reset@example.com", CONTEXT).rawToken();
        String siblingResetToken = passwordManagementPort.requestReset("reset@example.com", CONTEXT).rawToken();

        assertThat(jdbcTemplate.queryForObject(
            "select token_hash from auth_password_reset_tokens where user_id = ? order by created_at limit 1",
            String.class,
            user.userId()
        )).isNotEqualTo(resetToken);

        passwordManagementPort.resetPassword(resetToken, "Changed-password-456", CONTEXT);

        assertThatThrownBy(() -> passwordManagementPort.resetPassword(siblingResetToken, "Another-password-789", CONTEXT))
            .isInstanceOf(AuthValidationException.class)
            .hasMessageContaining("already consumed");
        assertThat(sessionPort.validate(existingLogin.sessionToken())).isEmpty();
        assertThat(login("reset@example.com", INITIAL_PASSWORD).authenticated()).isFalse();
        assertThat(login("reset@example.com", "Changed-password-456").authenticated()).isTrue();
    }

    @Test
    void enabledMfaFailsClosedWithoutCreatingSession() {
        RegistrationResult user = registerAndVerify("mfa@example.com");
        jdbcTemplate.update(
            """
            insert into auth_mfa_methods (id, user_id, type, status, created_at, enabled_at)
            values (?, ?, 'TOTP', 'ENABLED', now(), now())
            """,
            UUID.randomUUID(),
            user.userId()
        );

        LoginResult login = login("mfa@example.com", INITIAL_PASSWORD);

        assertThat(login.authenticated()).isFalse();
        assertThat(login.failureReason()).isEqualTo(LoginFailureReason.MFA_REQUIRED);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from auth_user_sessions where user_id = ?",
            Integer.class,
            user.userId()
        )).isZero();
    }

    @Test
    void managesRolesAndSuspensionWithTrustedAdminActor() {
        RegistrationResult admin = registerAndVerify("admin@example.com");
        bootstrapAdminRole(admin.userId());
        authenticateAs(admin.userId());

        RegistrationResult target = registerAndVerify("target@example.com");
        LoginResult targetLogin = login("target@example.com", INITIAL_PASSWORD);

        roleManagementPort.grant(target.userId(), Role.ADMIN, CONTEXT);
        assertThat(roleManagementPort.rolesFor(target.userId())).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(authorizationPort.hasRole(target.userId(), Role.ADMIN)).isTrue();
        assertThat(sessionPort.validate(targetLogin.sessionToken())).isEmpty();

        roleManagementPort.revoke(target.userId(), Role.ADMIN, CONTEXT);
        assertThat(roleManagementPort.rolesFor(target.userId())).containsExactly(Role.USER);
        assertThatThrownBy(() -> authorizationPort.requireRole(target.userId(), Role.ADMIN))
            .isInstanceOf(AuthValidationException.class);

        LoginResult secondTargetLogin = login("target@example.com", INITIAL_PASSWORD);
        accountAdministrationPort.suspend(target.userId(), CONTEXT);
        assertThat(statusOf(target.userId())).isEqualTo("SUSPENDED");
        assertThat(authorizationPort.hasRole(target.userId(), Role.USER)).isFalse();
        assertThat(sessionPort.validate(secondTargetLogin.sessionToken())).isEmpty();
        assertThat(login("target@example.com", INITIAL_PASSWORD).failureReason()).isEqualTo(LoginFailureReason.AUTHENTICATION_FAILED);
        assertThat(jdbcTemplate.queryForObject(
            "select actor_id from auth_security_audit_events where event_type = 'ACCOUNT_SUSPENDED' order by occurred_at desc limit 1",
            String.class
        )).isEqualTo(admin.userId().toString());
    }

    @Test
    void rejectsPrivilegedOperationsWithoutTrustedAdminActor() {
        RegistrationResult target = registerAndVerify("no-admin-target@example.com");

        assertThatThrownBy(() -> roleManagementPort.grant(target.userId(), Role.ADMIN, CONTEXT))
            .isInstanceOf(AuthValidationException.class)
            .hasMessageContaining("authenticated actor is required");

        RegistrationResult admin = registerAndVerify("self-admin@example.com");
        bootstrapAdminRole(admin.userId());
        authenticateAs(admin.userId());

        assertThatThrownBy(() -> roleManagementPort.revoke(admin.userId(), Role.ADMIN, CONTEXT))
            .isInstanceOf(AuthValidationException.class)
            .hasMessageContaining("own privileged roles");
    }

    @Test
    void duplicateEmailIsRejectedAndDatabaseMutationOfAuditEventsIsBlocked() {
        RegistrationResult user = register("duplicate@example.com");

        assertThatThrownBy(() -> register("DUPLICATE@example.com"))
            .isInstanceOf(AuthValidationException.class)
            .hasMessageContaining("email is already registered");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from auth_user_accounts where email = 'duplicate@example.com'",
            Integer.class
        )).isOne();

        UUID eventId = jdbcTemplate.queryForObject(
            "select id from auth_security_audit_events where user_id = ? order by occurred_at limit 1",
            UUID.class,
            user.userId()
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update auth_security_audit_events set details = 'changed' where id = ?",
            eventId
        )).rootCause().hasMessageContaining("auth security audit events are immutable after insert");
    }

    private RegistrationResult register(String email) {
        return registrationPort.register(new RegistrationCommand(email, "Test User", INITIAL_PASSWORD, CONTEXT));
    }

    private RegistrationResult registerAndVerify(String email) {
        RegistrationResult result = register(email);
        emailVerificationPort.verify(result.emailVerificationToken(), CONTEXT);
        return result;
    }

    private LoginResult login(String email, String password) {
        return authenticationPort.login(new LoginCommand(email, password, CONTEXT));
    }

    private LoginResult loginFromIp(String email, String password, String ipAddress) {
        return authenticationPort.login(new LoginCommand(email, password, new SecurityContextData(ipAddress, "integration-test")));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            userId.toString(),
            "test",
            AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private void bootstrapAdminRole(UUID userId) {
        jdbcTemplate.update(
            """
            insert into auth_role_grants (id, user_id, role, granted_by, granted_at)
            values (?, ?, 'ADMIN', ?, now())
            """,
            UUID.randomUUID(),
            userId,
            userId
        );
    }

    private String statusOf(UUID userId) {
        return jdbcTemplate.queryForObject("select status from auth_user_accounts where id = ?", String.class, userId);
    }

    private UUID sessionIdFor(UUID userId) {
        return jdbcTemplate.queryForObject("select id from auth_user_sessions where user_id = ?", UUID.class, userId);
    }
}
