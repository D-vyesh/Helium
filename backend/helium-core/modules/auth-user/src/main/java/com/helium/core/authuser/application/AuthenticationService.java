package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Credential;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.infrastructure.CredentialRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService implements AuthenticationPort {
    private final UserAccountRepository userAccountRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptThrottleService throttleService;
    private final AuthenticationTransactionService transactionService;
    private final String invalidCredentialHash;

    public AuthenticationService(
        UserAccountRepository userAccountRepository,
        CredentialRepository credentialRepository,
        PasswordHasher passwordHasher,
        LoginAttemptThrottleService throttleService,
        AuthenticationTransactionService transactionService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.throttleService = throttleService;
        this.transactionService = transactionService;
        this.invalidCredentialHash = passwordHasher.hash("HELIUM-invalid-credential-check-1");
    }

    @Override
    public LoginResult login(LoginCommand command) {
        String email = UserAccount.normalizeEmail(command.email());
        if (throttleService.isBlocked(email, command.securityContext().ipAddress())) {
            passwordHasher.matches(command.password(), invalidCredentialHash);
            return transactionService.recordAnonymousFailure(email, command.securityContext(), "source throttled");
        }

        UserAccount account = userAccountRepository.findByEmail(email).orElse(null);
        if (account == null) {
            passwordHasher.matches(command.password(), invalidCredentialHash);
            return transactionService.recordAnonymousFailure(email, command.securityContext(), "invalid credentials");
        }

        Credential credential = credentialRepository.findByUserId(account.id())
            .orElseThrow(() -> new IllegalStateException("credential is missing for user " + account.id()));
        if (!passwordHasher.matches(command.password(), credential.passwordHash())) {
            return transactionService.recordFailedLogin(account.id(), email, command.securityContext());
        }

        return transactionService.completeLogin(
            account.id(),
            credential.passwordChangedAt(),
            email,
            command.securityContext()
        );
    }
}
