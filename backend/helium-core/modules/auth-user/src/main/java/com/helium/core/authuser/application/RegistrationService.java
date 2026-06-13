package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.UserAccount;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService implements RegistrationPort {
    private final PasswordHasher passwordHasher;
    private final TokenCodec tokenCodec;
    private final RegistrationTransactionService transactionService;

    public RegistrationService(
        PasswordHasher passwordHasher,
        TokenCodec tokenCodec,
        RegistrationTransactionService transactionService
    ) {
        this.passwordHasher = passwordHasher;
        this.tokenCodec = tokenCodec;
        this.transactionService = transactionService;
    }

    @Override
    public RegistrationResult register(RegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.securityContext(), "securityContext");
        PasswordPolicy.validate(command.password());
        String email = UserAccount.normalizeEmail(command.email());
        String passwordHash = passwordHasher.hash(command.password());
        TokenValue token = tokenCodec.generate();

        try {
            UUID userId = transactionService.register(
                email,
                command.displayName(),
                passwordHash,
                token.tokenHash(),
                command.securityContext()
            );
            return new RegistrationResult(userId, token.rawToken());
        } catch (DataIntegrityViolationException exception) {
            return new RegistrationResult(UUID.randomUUID(), token.rawToken());
        }
    }
}
