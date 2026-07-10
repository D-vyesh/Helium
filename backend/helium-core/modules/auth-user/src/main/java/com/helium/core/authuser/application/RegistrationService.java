package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.UserAccount;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService implements RegistrationPort {
    private final PasswordHasher passwordHasher;
    private final TokenCodec tokenCodec;
    private final RegistrationTransactionService transactionService;
    private final EmailService emailService;

    public RegistrationService(
        PasswordHasher passwordHasher,
        TokenCodec tokenCodec,
        RegistrationTransactionService transactionService,
        EmailService emailService
    ) {
        this.passwordHasher = passwordHasher;
        this.tokenCodec = tokenCodec;
        this.transactionService = transactionService;
        this.emailService = emailService;
    }

    @Override
    public RegistrationResult register(RegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.securityContext(), "securityContext");
        PasswordPolicy.validate(command.password());
        String email = UserAccount.normalizeEmail(command.email());
        String displayName = command.displayName() != null ? command.displayName() : email.split("@")[0];
        String passwordHash = passwordHasher.hash(command.password());
        TokenValue token = tokenCodec.generate();

        UUID userId = transactionService.register(
            email,
            displayName,
            passwordHash,
            token.tokenHash(),
            command.securityContext()
        );

        // Send verification email — token is NEVER returned to the client
        String verificationUrl = command.verificationBaseUrl() + "/email-verification?token=" + token.rawToken();
        emailService.sendVerificationEmail(email, displayName, verificationUrl);

        return new RegistrationResult(userId);
    }
}
