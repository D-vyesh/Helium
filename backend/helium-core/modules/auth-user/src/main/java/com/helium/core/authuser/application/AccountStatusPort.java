package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.UserAccountStatus;
import java.util.UUID;

public interface AccountStatusPort {
    boolean isActive(UUID userId);

    UserAccountStatus statusOf(UUID userId);
}
