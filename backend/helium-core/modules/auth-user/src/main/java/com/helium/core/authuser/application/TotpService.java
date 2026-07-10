package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.MfaMethod;
import com.helium.core.authuser.domain.MfaSession;
import com.helium.core.authuser.domain.MfaStatus;
import com.helium.core.authuser.domain.MfaType;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.TotpBackupCode;
import com.helium.core.authuser.domain.TotpSecret;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.UserSession;
import com.helium.core.authuser.infrastructure.MfaMethodRepository;
import com.helium.core.authuser.infrastructure.MfaSessionRepository;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.TotpBackupCodeRepository;
import com.helium.core.authuser.infrastructure.TotpSecretRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import com.helium.core.authuser.infrastructure.UserSessionRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full RFC 6238 TOTP implementation with:
 * - AES-256-GCM encrypted secret storage
 * - 8 single-use backup codes
 * - MFA session tokens (5-minute TTL) for the login challenge flow
 * - Replay protection via consumed MFA sessions
 */
@Service
public class TotpService implements TotpPort {

    private static final int BACKUP_CODE_COUNT = 8;
    private static final int TOTP_WINDOW = 1; // ±1 step (30s each side)
    private static final long TOTP_STEP_SECONDS = 30L;
    private static final int TOTP_DIGITS = 6;
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    private final TotpSecretRepository totpSecretRepository;
    private final TotpBackupCodeRepository backupCodeRepository;
    private final MfaMethodRepository mfaMethodRepository;
    private final MfaSessionRepository mfaSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final LoginAttemptHistoryService loginAttemptHistoryService;
    private final SecurityAuditService auditService;
    private final TokenCodec tokenCodec;
    private final Clock clock;
    private final byte[] encryptionKey;

    public TotpService(
        TotpSecretRepository totpSecretRepository,
        TotpBackupCodeRepository backupCodeRepository,
        MfaMethodRepository mfaMethodRepository,
        MfaSessionRepository mfaSessionRepository,
        UserAccountRepository userAccountRepository,
        UserSessionRepository userSessionRepository,
        RoleGrantRepository roleGrantRepository,
        LoginAttemptHistoryService loginAttemptHistoryService,
        SecurityAuditService auditService,
        TokenCodec tokenCodec,
        Clock clock,
        @Value("${helium.auth.totp-encryption-key:local-totp-encryption-key-32bytes!!}") String encryptionKeyStr
    ) {
        this.totpSecretRepository = totpSecretRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.mfaMethodRepository = mfaMethodRepository;
        this.mfaSessionRepository = mfaSessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.loginAttemptHistoryService = loginAttemptHistoryService;
        this.auditService = auditService;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
        // Derive a 32-byte key from the configured string
        byte[] raw = encryptionKeyStr.getBytes(StandardCharsets.UTF_8);
        this.encryptionKey = deriveKey(raw);
    }

    @Override
    @Transactional
    public TotpSetupResult beginSetup(UUID userId, SecurityContextData securityContext) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        if (!account.canAuthenticate(clock.instant())) {
            throw new AuthValidationException("account cannot enable MFA");
        }

        // Generate a new 20-byte (160-bit) TOTP secret
        byte[] secretBytes = new byte[20];
        new SecureRandom().nextBytes(secretBytes);
        String base32Secret = base32Encode(secretBytes);

        // Encrypt and persist
        String encrypted = encrypt(base32Secret);
        Instant now = clock.instant();
        totpSecretRepository.findByUserId(userId).ifPresentOrElse(
            existing -> existing.updateSecret(encrypted, now),
            () -> totpSecretRepository.save(TotpSecret.create(userId, encrypted, now))
        );

        // Ensure a PENDING MFA method exists
        if (!mfaMethodRepository.existsByUserIdAndStatus(userId, MfaStatus.PENDING)
            && !mfaMethodRepository.existsByUserIdAndStatus(userId, MfaStatus.ENABLED)) {
            mfaMethodRepository.save(MfaMethod.pendingTotp(userId, now));
        }

        String issuer = "HELIUM";
        String otpAuthUrl = String.format(
            "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
            issuer, account.email(), base32Secret, issuer, TOTP_DIGITS, TOTP_STEP_SECONDS
        );
        String qrDataUrl = generateQrDataUrl(otpAuthUrl);

        auditService.record(SecurityAuditEventType.MFA_SETUP_INITIATED, userId, null, securityContext, "TOTP setup initiated");
        return new TotpSetupResult(base32Secret, otpAuthUrl, qrDataUrl);
    }

    @Override
    @Transactional
    public TotpConfirmResult confirmSetup(UUID userId, String totpCode, SecurityContextData securityContext) {
        TotpSecret totpSecret = totpSecretRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthValidationException("TOTP setup has not been initiated"));
        String rawSecret = decrypt(totpSecret.encryptedSecret());

        if (!verifyTotp(rawSecret, totpCode)) {
            throw new AuthValidationException("TOTP code is invalid");
        }

        Instant now = clock.instant();
        // Activate the MFA method
        mfaMethodRepository.findByUserIdAndType(userId, MfaType.TOTP).ifPresent(method -> method.enable(now));

        // Generate backup codes
        List<String> rawCodes = generateBackupCodes();
        backupCodeRepository.deleteAllByUserId(userId);
        rawCodes.forEach(code ->
            backupCodeRepository.save(TotpBackupCode.create(userId, sha256Hex(code), now))
        );

        auditService.record(SecurityAuditEventType.MFA_ENABLED, userId, null, securityContext, "TOTP MFA enabled");
        return new TotpConfirmResult(true, rawCodes);
    }

    @Override
    @Transactional
    public void disable(UUID userId, String totpCode, SecurityContextData securityContext) {
        TotpSecret totpSecret = totpSecretRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthValidationException("TOTP is not configured"));
        if (!verifyTotp(decrypt(totpSecret.encryptedSecret()), totpCode)) {
            throw new AuthValidationException("TOTP code is invalid");
        }
        Instant now = clock.instant();
        mfaMethodRepository.findByUserIdAndType(userId, MfaType.TOTP).ifPresent(method -> method.disable(now));
        backupCodeRepository.deleteAllByUserId(userId);
        auditService.record(SecurityAuditEventType.MFA_DISABLED, userId, null, securityContext, "TOTP MFA disabled");
    }

    @Override
    @Transactional
    public LoginResult completeChallenge(String mfaSessionToken, String totpCode, SecurityContextData securityContext) {
        MfaSession mfaSession = mfaSessionRepository.findByTokenHash(tokenCodec.hash(mfaSessionToken))
            .orElseThrow(() -> new AuthValidationException("MFA session is invalid"));
        Instant now = clock.instant();
        mfaSession.consume(now);

        UUID userId = mfaSession.userId();
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        if (!account.canAuthenticate(now)) {
            throw new AuthValidationException("account cannot authenticate");
        }

        TotpSecret totpSecret = totpSecretRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthValidationException("TOTP is not configured"));
        if (!verifyTotp(decrypt(totpSecret.encryptedSecret()), totpCode)) {
            loginAttemptHistoryService.record(userId, account.email(), false, "invalid TOTP code", securityContext);
            auditService.record(SecurityAuditEventType.AUTH_LOGIN_FAILED, userId, null, securityContext, "invalid TOTP code");
            throw new AuthValidationException("TOTP code is invalid");
        }

        return createSession(userId, account, securityContext, now);
    }

    @Override
    @Transactional
    public LoginResult completeWithBackupCode(String mfaSessionToken, String backupCode, SecurityContextData securityContext) {
        MfaSession mfaSession = mfaSessionRepository.findByTokenHash(tokenCodec.hash(mfaSessionToken))
            .orElseThrow(() -> new AuthValidationException("MFA session is invalid"));
        Instant now = clock.instant();
        mfaSession.consume(now);

        UUID userId = mfaSession.userId();
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        if (!account.canAuthenticate(now)) {
            throw new AuthValidationException("account cannot authenticate");
        }

        TotpBackupCode code = backupCodeRepository.findByUserIdAndCodeHash(userId, sha256Hex(backupCode.trim().toUpperCase()))
            .orElseThrow(() -> new AuthValidationException("backup code is invalid"));
        if (code.isUsed()) {
            throw new AuthValidationException("backup code has already been used");
        }
        code.consume(now);

        auditService.record(SecurityAuditEventType.MFA_BACKUP_CODE_USED, userId, null, securityContext, "backup code used for login");
        return createSession(userId, account, securityContext, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listBackupCodes(UUID userId) {
        // We only return count info — raw codes are shown once at setup
        int remaining = backupCodeRepository.findAllByUserIdAndUsedAtIsNull(userId).size();
        return List.of(remaining + " backup codes remaining");
    }

    @Override
    @Transactional
    public List<String> regenerateBackupCodes(UUID userId, String totpCode, SecurityContextData securityContext) {
        TotpSecret totpSecret = totpSecretRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthValidationException("TOTP is not configured"));
        if (!verifyTotp(decrypt(totpSecret.encryptedSecret()), totpCode)) {
            throw new AuthValidationException("TOTP code is invalid");
        }
        Instant now = clock.instant();
        List<String> rawCodes = generateBackupCodes();
        backupCodeRepository.deleteAllByUserId(userId);
        rawCodes.forEach(code ->
            backupCodeRepository.save(TotpBackupCode.create(userId, sha256Hex(code), now))
        );
        auditService.record(SecurityAuditEventType.MFA_BACKUP_CODES_REGENERATED, userId, null, securityContext, "backup codes regenerated");
        return rawCodes;
    }

    @Override
    @Transactional
    public void verifySensitiveAction(UUID userId, String totpCode, SecurityContextData securityContext) {
        boolean enabled = mfaMethodRepository.findByUserIdAndType(userId, MfaType.TOTP)
            .map(method -> method.status() == MfaStatus.ENABLED)
            .orElse(false);
        if (!enabled) {
            throw new AuthValidationException("enabled TOTP MFA is required for this action");
        }
        TotpSecret totpSecret = totpSecretRepository.findByUserId(userId)
            .orElseThrow(() -> new AuthValidationException("TOTP is not configured"));
        if (!verifyTotp(decrypt(totpSecret.encryptedSecret()), totpCode)) {
            throw new AuthValidationException("TOTP code is invalid");
        }
        auditService.record(SecurityAuditEventType.MFA_SENSITIVE_ACTION_VERIFIED, userId, null, securityContext, "sensitive action MFA verified");
    }

    /** Create an MFA session token for the login challenge flow. */
    @Transactional
    public String createMfaSession(UUID userId) {
        TokenValue token = tokenCodec.generate();
        mfaSessionRepository.save(MfaSession.create(userId, token.tokenHash(), clock.instant()));
        return token.rawToken();
    }

    // ---- TOTP RFC 6238 ----

    public boolean verifyTotp(String base32Secret, String code) {
        if (code == null || code.length() != TOTP_DIGITS) return false;
        long counter = clock.instant().getEpochSecond() / TOTP_STEP_SECONDS;
        byte[] key = base32Decode(base32Secret);
        for (int i = -TOTP_WINDOW; i <= TOTP_WINDOW; i++) {
            if (generateTotp(key, counter + i).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generateTotp(byte[] key, long counter) {
        try {
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    // ---- Encryption (AES-256-GCM) ----

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP secret encryption failed", e);
        }
    }

    private String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP secret decryption failed", e);
        }
    }

    private static byte[] deriveKey(byte[] input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Helpers ----

    private LoginResult createSession(UUID userId, UserAccount account, SecurityContextData securityContext, Instant now) {
        TokenValue token = tokenCodec.generate();
        UserSession session = UserSession.create(
            userId, token.tokenHash(), securityContext.ipAddress(),
            securityContext.userAgent(), SESSION_LIFETIME, now
        );
        userSessionRepository.save(session);
        Set<Role> roles = roleGrantRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
            .map(grant -> grant.role())
            .collect(Collectors.toUnmodifiableSet());
        loginAttemptHistoryService.record(userId, account.email(), true, null, securityContext);
        auditService.record(SecurityAuditEventType.AUTH_LOGIN_SUCCESS, userId, session.id(), securityContext, "MFA login succeeded");
        return LoginResult.succeeded(userId, token.rawToken(), session.expiresAt(), roles);
    }

    private List<String> generateBackupCodes() {
        SecureRandom rng = new SecureRandom();
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            // Format: XXXX-XXXX (8 uppercase alphanumeric chars)
            String code = String.format("%04X-%04X", rng.nextInt(0xFFFF), rng.nextInt(0xFFFF));
            codes.add(code);
        }
        return codes;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // Minimal Base32 encoder/decoder (RFC 4648, no padding required for TOTP)
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String encoded) {
        String upper = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int outputLen = upper.length() * 5 / 8;
        byte[] result = new byte[outputLen];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : upper.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                result[idx++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return result;
    }

    private static String generateQrDataUrl(String otpAuthUrl) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("TOTP QR code generation failed", exception);
        }
    }
}
