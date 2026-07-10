package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService implements UserProfilePort {
    private final UserAccountRepository userAccountRepository;

    public UserProfileService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileView requireProfile(UUID userId) {
        var account = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthValidationException("user account was not found"));
        return new UserProfileView(account.id(), account.email(), account.displayName());
    }
}
