package com.helium.core.admin.application;

import com.helium.core.authuser.domain.Role;
import java.util.UUID;

public interface AdminOperationsPort {
    void suspendUser(UUID userId);

    void grantRole(UUID userId, Role role);

    void revokeRole(UUID userId, Role role);

    void updateMarket(String symbol, boolean enabled);
}
