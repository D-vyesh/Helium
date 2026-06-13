package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Role;
import java.util.UUID;

public interface AuthorizationPort {
    boolean hasRole(UUID userId, Role role);

    void requireRole(UUID userId, Role role);

    UUID requireCurrentActor(Role role);
}
