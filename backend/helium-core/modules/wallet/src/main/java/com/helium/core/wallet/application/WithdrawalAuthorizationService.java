package com.helium.core.wallet.application;

import com.helium.core.authuser.application.EmailService;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.application.TotpPort;
import com.helium.core.authuser.application.UserProfilePort;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalAuthorization;
import com.helium.core.wallet.infrastructure.WithdrawalAuthorizationRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalAuthorizationService {
    private static final Set<String> NATIVE_ASSETS = Set.of("BTC", "ETH", "SOL");
    private static final Duration EMAIL_TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final WithdrawalAuthorizationRepository authorizationRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final WalletActorService actorService;
    private final UserProfilePort userProfilePort;
    private final EmailService emailService;
    private final TotpPort totpPort;
    private final WithdrawalQueueService withdrawalQueueService;
    private final WalletAuditService auditService;
    private final Clock clock;
    private final String publicBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public WithdrawalAuthorizationService(
        WithdrawalAuthorizationRepository authorizationRepository,
        WithdrawalRepository withdrawalRepository,
        WalletActorService actorService,
        UserProfilePort userProfilePort,
        EmailService emailService,
        TotpPort totpPort,
        WithdrawalQueueService withdrawalQueueService,
        WalletAuditService auditService,
        Clock clock,
        @Value("${helium.email.base-url:http://localhost:3000}") String publicBaseUrl
    ) {
        this.authorizationRepository = authorizationRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.actorService = actorService;
        this.userProfilePort = userProfilePort;
        this.emailService = emailService;
        this.totpPort = totpPort;
        this.withdrawalQueueService = withdrawalQueueService;
        this.auditService = auditService;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void issueIfRequired(Withdrawal withdrawal) {
        if (!requiresAuthorization(withdrawal)) {
            return;
        }
        if (authorizationRepository.findByWithdrawalIdForUpdate(withdrawal.id()).isPresent()) {
            return;
        }
        issue(withdrawal, WithdrawalAuthorization::issue);
    }

    @Transactional
    public WithdrawalAuthorizationView resend(UUID withdrawalId) {
        Withdrawal withdrawal = requireOwnedWithdrawal(withdrawalId);
        if (!requiresAuthorization(withdrawal)) {
            throw new WalletValidationException("withdrawal authorization is not required for this asset");
        }
        WithdrawalAuthorization authorization = authorizationRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal authorization was not found"));
        if (authorization.emailConfirmedAt() != null) {
            return toView(authorization);
        }
        String rawToken = newRawToken();
        Instant now = clock.instant();
        authorization.rotateEmailToken(sha256(rawToken), now.plus(EMAIL_TOKEN_LIFETIME), now);
        sendEmail(withdrawal, rawToken);
        auditService.record(WalletAuditEventType.WITHDRAWAL_EMAIL_CONFIRMATION_ISSUED, withdrawal.id(), withdrawal.userId().toString(), "resent");
        return toView(authorization);
    }

    @Transactional
    public WithdrawalAuthorizationView confirmEmail(UUID withdrawalId, String rawToken) {
        Withdrawal withdrawal = requireOwnedWithdrawal(withdrawalId);
        WithdrawalAuthorization authorization = authorizationRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal authorization was not found"));
        authorization.confirmEmail(sha256(BlockchainNetwork.requireText(rawToken, "token", 512)), clock.instant());
        auditService.record(WalletAuditEventType.WITHDRAWAL_EMAIL_CONFIRMED, withdrawal.id(), withdrawal.userId().toString(), withdrawal.clientRequestId());
        beginValidationIfReady(withdrawal, authorization);
        return toView(authorization);
    }

    @Transactional
    public WithdrawalAuthorizationView confirmMfa(UUID withdrawalId, String totpCode, SecurityContextData securityContext) {
        Withdrawal withdrawal = requireOwnedWithdrawal(withdrawalId);
        WithdrawalAuthorization authorization = authorizationRepository.findByWithdrawalIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal authorization was not found"));
        totpPort.verifySensitiveAction(withdrawal.userId(), totpCode, securityContext);
        authorization.confirmMfa(clock.instant());
        auditService.record(WalletAuditEventType.WITHDRAWAL_MFA_CONFIRMED, withdrawal.id(), withdrawal.userId().toString(), withdrawal.clientRequestId());
        beginValidationIfReady(withdrawal, authorization);
        return toView(authorization);
    }

    @Transactional(readOnly = true)
    public void requireConfirmed(Withdrawal withdrawal) {
        if (!requiresAuthorization(withdrawal)) {
            return;
        }
        WithdrawalAuthorization authorization = authorizationRepository.findByWithdrawalId(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal authorization was not found"));
        if (!authorization.isConfirmed()) {
            throw new WalletValidationException("withdrawal requires both email and MFA confirmation");
        }
    }

    private void issue(Withdrawal withdrawal, AuthorizationIssuer issuer) {
        String rawToken = newRawToken();
        Instant now = clock.instant();
        WithdrawalAuthorization authorization = issuer.issue(
            withdrawal.id(),
            withdrawal.userId(),
            sha256(rawToken),
            now.plus(EMAIL_TOKEN_LIFETIME),
            now
        );
        authorizationRepository.save(authorization);
        sendEmail(withdrawal, rawToken);
        auditService.record(WalletAuditEventType.WITHDRAWAL_EMAIL_CONFIRMATION_ISSUED, withdrawal.id(), withdrawal.userId().toString(), "issued");
    }

    private void sendEmail(Withdrawal withdrawal, String rawToken) {
        var profile = userProfilePort.requireProfile(withdrawal.userId());
        String url = publicBaseUrl + "/wallet/withdraw/confirm?withdrawalId=" + withdrawal.id() + "&token=" + rawToken;
        emailService.sendWithdrawalConfirmationEmail(
            profile.email(),
            profile.displayName(),
            url,
            withdrawal.assetCode(),
            withdrawal.amount().toPlainString(),
            withdrawal.destinationAddress()
        );
    }

    private Withdrawal requireOwnedWithdrawal(UUID withdrawalId) {
        UUID userId = actorService.requireCurrentUserId();
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal was not found"));
        if (!withdrawal.userId().equals(userId)) {
            throw new WalletValidationException("withdrawal does not belong to the authenticated user");
        }
        return withdrawal;
    }

    private void beginValidationIfReady(Withdrawal withdrawal, WithdrawalAuthorization authorization) {
        if (authorization.isConfirmed()) {
            withdrawalQueueService.beginValidation(withdrawal, withdrawal.userId().toString());
        }
    }

    private static boolean requiresAuthorization(Withdrawal withdrawal) {
        return NATIVE_ASSETS.contains(withdrawal.assetCode());
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static WithdrawalAuthorizationView toView(WithdrawalAuthorization authorization) {
        return new WithdrawalAuthorizationView(
            authorization.withdrawalId(),
            authorization.emailConfirmedAt() != null,
            authorization.mfaConfirmedAt() != null,
            authorization.emailExpiresAt()
        );
    }

    @FunctionalInterface
    private interface AuthorizationIssuer {
        WithdrawalAuthorization issue(UUID withdrawalId, UUID userId, String tokenHash, Instant expiresAt, Instant now);
    }
}
