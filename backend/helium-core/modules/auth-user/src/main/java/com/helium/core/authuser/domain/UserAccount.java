package com.helium.core.authuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "auth_user_accounts",
    uniqueConstraints = @UniqueConstraint(name = "uk_auth_user_accounts_email", columnNames = "email")
)
public class UserAccount {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private UserAccountStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserAccount() {
    }

    private UserAccount(String email, String displayName, Instant now) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.displayName = requireText(displayName, "displayName", 120);
        this.status = UserAccountStatus.PENDING_VERIFICATION;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static UserAccount register(String email, String displayName, Instant now) {
        return new UserAccount(email, displayName, now);
    }

    public void verifyEmail(Instant now) {
        if (status == UserAccountStatus.CLOSED) {
            throw new AuthValidationException("closed account cannot verify email");
        }
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
        if (status == UserAccountStatus.PENDING_VERIFICATION) {
            status = UserAccountStatus.ACTIVE;
        }
        updatedAt = now;
    }

    public boolean recordFailedLogin(int maximumAttempts, Duration lockDuration, Instant now) {
        requirePositive(maximumAttempts, "maximumAttempts");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (status == UserAccountStatus.SUSPENDED || status == UserAccountStatus.CLOSED) {
            return false;
        }
        failedLoginAttempts++;
        updatedAt = now;
        if (failedLoginAttempts >= maximumAttempts) {
            status = UserAccountStatus.LOCKED;
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        if (!canAuthenticate(now)) {
            throw new AuthValidationException("account cannot authenticate");
        }
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public boolean canAuthenticate(Instant now) {
        refreshExpiredLock(now);
        return status == UserAccountStatus.ACTIVE;
    }

    public void suspend(Instant now) {
        if (status == UserAccountStatus.CLOSED) {
            throw new AuthValidationException("closed account cannot be suspended");
        }
        status = UserAccountStatus.SUSPENDED;
        lockedUntil = null;
        updatedAt = now;
    }

    public void reactivate(Instant now) {
        if (status != UserAccountStatus.SUSPENDED) {
            throw new AuthValidationException("only suspended accounts can be reactivated");
        }
        status = emailVerifiedAt == null ? UserAccountStatus.PENDING_VERIFICATION : UserAccountStatus.ACTIVE;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void unlock(Instant now) {
        if (status != UserAccountStatus.LOCKED) {
            throw new AuthValidationException("only locked accounts can be unlocked");
        }
        status = emailVerifiedAt == null ? UserAccountStatus.PENDING_VERIFICATION : UserAccountStatus.ACTIVE;
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    private void refreshExpiredLock(Instant now) {
        if (status == UserAccountStatus.LOCKED && lockedUntil != null && !lockedUntil.isAfter(now)) {
            status = emailVerifiedAt == null ? UserAccountStatus.PENDING_VERIFICATION : UserAccountStatus.ACTIVE;
            failedLoginAttempts = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public UserAccountStatus status() {
        return status;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public static String normalizeEmail(String value) {
        String email = requireText(value, "email", 320).toLowerCase(Locale.ROOT);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new AuthValidationException("email is invalid");
        }
        return email;
    }

    private static String requireText(String value, String field, int maximumLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) {
            throw new AuthValidationException(field + " is required");
        }
        if (text.length() > maximumLength) {
            throw new AuthValidationException(field + " is too long");
        }
        return text;
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new AuthValidationException(field + " must be positive");
        }
    }
}
