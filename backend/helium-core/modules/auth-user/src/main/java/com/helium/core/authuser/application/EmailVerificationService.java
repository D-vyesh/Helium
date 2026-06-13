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
    private final Clock clock;

    public EmailVerificationService(
        UserAccountRepository userAccountRepository,
        EmailVerificationTokenRepository tokenRepository,
        TokenCodec tokenCodec,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.tokenCodec = tokenCodec;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String issue(UUID userId, SecurityContextData securityContext) {
        UserAccount account = userAccountRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        if (account.emailVerifiedAt() != null || account.status() == UserAccountStatus.CLOSED) {
            throw new AuthValidationException("email verification is not available for this account");
        }
        Instant now = clock.instant();
        TokenValue token = tokenCodec.generate();
        tokenRepository.save(EmailVerificationToken.issue(userId, token.tokenHash(), TOKEN_LIFETIME, now));
        auditService.record(
            SecurityAuditEventType.EMAIL_VERIFICATION_ISSUED,
            userId,
            null,
            securityContext,
            "email verification token issued"
        );
        return token.rawToken();
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
        auditService.record(SecurityAuditEventType.EMAIL_VERIFIED, account.id(), null, securityContext, "email verified");
    }
}
