package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.UserAccountStatus;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountStatusService implements AccountStatusPort {
    private final UserAccountRepository userAccountRepository;

    public AccountStatusService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        return statusOf(userId) == UserAccountStatus.ACTIVE;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountStatus statusOf(UUID userId) {
        return userAccountRepository.findById(userId)
            .map(account -> account.status())
            .orElse(UserAccountStatus.CLOSED);
    }
}
