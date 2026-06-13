package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Credential;
import com.helium.core.authuser.domain.EmailVerificationToken;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.RoleGrant;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.infrastructure.CredentialRepository;
import com.helium.core.authuser.infrastructure.EmailVerificationTokenRepository;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationTransactionService {
    private static final Duration VERIFICATION_TOKEN_LIFETIME = Duration.ofHours(24);

    private final UserAccountRepository userAccountRepository;
    private final CredentialRepository credentialRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public RegistrationTransactionService(
        UserAccountRepository userAccountRepository,
        CredentialRepository credentialRepository,
        RoleGrantRepository roleGrantRepository,
        EmailVerificationTokenRepository verificationTokenRepository,
        SecurityAuditService auditService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public UUID register(
        String email,
        String displayName,
        String passwordHash,
        String verificationTokenHash,
        SecurityContextData securityContext
    ) {
        Instant now = clock.instant();
        UserAccount account = UserAccount.register(email, displayName, now);
        userAccountRepository.saveAndFlush(account);
        credentialRepository.save(Credential.create(account.id(), passwordHash, now));
        roleGrantRepository.save(RoleGrant.grant(account.id(), Role.USER, account.id(), now));
        verificationTokenRepository.save(EmailVerificationToken.issue(
            account.id(),
            verificationTokenHash,
            VERIFICATION_TOKEN_LIFETIME,
            now
        ));
        auditService.record(SecurityAuditEventType.USER_REGISTERED, account.id(), null, securityContext, "user registered");
        auditService.record(
            SecurityAuditEventType.EMAIL_VERIFICATION_ISSUED,
            account.id(),
            null,
            securityContext,
            "email verification token issued"
        );
        return account.id();
    }
}
