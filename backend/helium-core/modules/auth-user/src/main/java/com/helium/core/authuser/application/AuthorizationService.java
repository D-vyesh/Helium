package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.UserAccountStatus;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService implements AuthorizationPort {
    private final RoleGrantRepository roleGrantRepository;
    private final UserAccountRepository userAccountRepository;
    private final TrustedActorProvider trustedActorProvider;

    public AuthorizationService(
        RoleGrantRepository roleGrantRepository,
        UserAccountRepository userAccountRepository,
        TrustedActorProvider trustedActorProvider
    ) {
        this.roleGrantRepository = roleGrantRepository;
        this.userAccountRepository = userAccountRepository;
        this.trustedActorProvider = trustedActorProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(UUID userId, Role role) {
        return userAccountRepository.findById(userId)
            .filter(account -> account.status() == UserAccountStatus.ACTIVE)
            .filter(account -> roleGrantRepository.existsByUserIdAndRoleAndRevokedAtIsNull(userId, role))
            .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireRole(UUID userId, Role role) {
        if (!hasRole(userId, role)) {
            throw new AuthValidationException("required role is not granted");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireCurrentActor(Role role) {
        UUID actorId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new AuthValidationException("authenticated actor is required"));
        requireRole(actorId, role);
        return actorId;
    }
}
