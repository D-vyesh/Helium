package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.EmailVerificationToken;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.UserAccountStatus;
import com.helium.core.authuser.infrastructure.EmailVerificationTokenRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService implements EmailVerificationPort {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final TokenCodec tokenCodec;
    private final SecurityAuditService auditService;
    private final EmailService emailService;
    private final Clock clock;

    public EmailVerificationService(
        UserAccountRepository userAccountRepository,
        EmailVerificationTokenRepository tokenRepository,
        TokenCodec tokenCodec,
        SecurityAuditService auditService,
        EmailService emailService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.tokenCodec = tokenCodec;
        this.auditService = auditService;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void issue(UUID userId, SecurityContextData securityContext) {
        issue(userId, "http://localhost:3000", securityContext);
    }

    @Override
    @Transactional
    public void issue(UUID userId, String baseUrl, SecurityContextData securityContext) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        if (account.emailVerifiedAt() != null || account.status() == UserAccountStatus.CLOSED) {
            throw new AuthValidationException("email verification is not available for this account");
        }
        Instant now = clock.instant();
        // Invalidate any existing tokens before issuing a new one
        tokenRepository.findAllByUserIdAndConsumedAtIsNull(account.id()).forEach(t -> t.invalidate(now));
        TokenValue token = tokenCodec.generate();
        tokenRepository.save(EmailVerificationToken.issue(userId, token.tokenHash(), TOKEN_LIFETIME, now));
        auditService.record(
            SecurityAuditEventType.EMAIL_VERIFICATION_ISSUED,
            userId,
            null,
            securityContext,
            "email verification token issued"
        );
        String verificationUrl = baseUrl + "/email-verification?token=" + token.rawToken();
        emailService.sendVerificationEmail(account.email(), account.displayName(), verificationUrl);
    }

    @Override
    @Transactional
    public void resend(String email, String baseUrl, SecurityContextData securityContext) {
        UserAccount account = userAccountRepository.findByEmailForUpdate(UserAccount.normalizeEmail(email)).orElse(null);
        if (account == null || account.emailVerifiedAt() != null || account.status() == UserAccountStatus.CLOSED) {
            auditService.record(
                SecurityAuditEventType.EMAIL_RESEND_REQUESTED,
                account == null ? null : account.id(),
                null,
                securityContext,
                "email verification resend accepted"
            );
            return;
        }
        issue(account.id(), baseUrl, securityContext);
        auditService.record(
            SecurityAuditEventType.EMAIL_RESEND_REQUESTED,
            account.id(),
            null,
            securityContext,
            "email verification resent"
        );
    }

    @Override
    @Transactional
    public void verify(String rawToken, SecurityContextData securityContext) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenCodec.hash(rawToken))
            .orElseThrow(() -> new AuthValidationException("email verification token is invalid"));
        Instant now = clock.instant();
        token.consume(now);
        UserAccount account = userAccountRepository.findByIdForUpdate(token.userId())
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        account.verifyEmail(now);
        tokenRepository.findAllByUserIdAndConsumedAtIsNull(account.id()).forEach(activeToken -> activeToken.invalidate(now));
        auditService.record(SecurityAuditEventType.AUTH_EMAIL_VERIFIED, account.id(), null, securityContext, "email verified");
    }
}
