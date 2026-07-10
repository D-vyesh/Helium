package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persisted, single-use email and MFA gates for a native-asset withdrawal. */
@Entity
@Table(name = "wallet_withdrawal_authorizations")
public class WithdrawalAuthorization {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false, updatable = false, unique = true)
    private UUID withdrawalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email_token_hash", nullable = false, length = 64)
    private String emailTokenHash;

    @Column(name = "email_expires_at", nullable = false)
    private Instant emailExpiresAt;

    @Column(name = "email_confirmed_at")
    private Instant emailConfirmedAt;

    @Column(name = "mfa_confirmed_at")
    private Instant mfaConfirmedAt;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WithdrawalAuthorization() {
    }

    private WithdrawalAuthorization(UUID withdrawalId, UUID userId, String emailTokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.emailTokenHash = hash(emailTokenHash);
        this.emailExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.issuedAt = Objects.requireNonNull(now, "now");
        if (!expiresAt.isAfter(now)) {
            throw new WalletValidationException("email confirmation expiry must be in the future");
        }
    }

    public static WithdrawalAuthorization issue(UUID withdrawalId, UUID userId, String emailTokenHash, Instant expiresAt, Instant now) {
        return new WithdrawalAuthorization(withdrawalId, userId, emailTokenHash, expiresAt, now);
    }

    public void rotateEmailToken(String nextTokenHash, Instant expiresAt, Instant now) {
        if (emailConfirmedAt != null) {
            throw new WalletValidationException("withdrawal email confirmation is already complete");
        }
        if (!expiresAt.isAfter(now)) {
            throw new WalletValidationException("email confirmation expiry must be in the future");
        }
        this.emailTokenHash = hash(nextTokenHash);
        this.emailExpiresAt = expiresAt;
        this.issuedAt = now;
    }

    public void confirmEmail(String presentedTokenHash, Instant now) {
        if (!MessageDigest.isEqual(
            emailTokenHash.getBytes(StandardCharsets.US_ASCII),
            hash(presentedTokenHash).getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new WalletValidationException("withdrawal email confirmation token is invalid");
        }
        if (emailConfirmedAt != null) {
            return;
        }
        if (!emailExpiresAt.isAfter(now)) {
            throw new WalletValidationException("withdrawal email confirmation token has expired");
        }
        emailConfirmedAt = Objects.requireNonNull(now, "now");
    }

    public void confirmMfa(Instant now) {
        if (mfaConfirmedAt == null) {
            mfaConfirmedAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean isConfirmed() {
        return emailConfirmedAt != null && mfaConfirmedAt != null;
    }

    public UUID withdrawalId() {
        return withdrawalId;
    }

    public UUID userId() {
        return userId;
    }

    public Instant emailExpiresAt() {
        return emailExpiresAt;
    }

    public Instant emailConfirmedAt() {
        return emailConfirmedAt;
    }

    public Instant mfaConfirmedAt() {
        return mfaConfirmedAt;
    }

    private static String hash(String value) {
        String hash = BlockchainNetwork.requireText(value, "emailTokenHash", 64);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new WalletValidationException("email confirmation token hash is invalid");
        }
        return hash;
    }
}
