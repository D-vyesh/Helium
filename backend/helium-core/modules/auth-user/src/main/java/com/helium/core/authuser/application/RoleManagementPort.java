package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Role;
import java.util.Set;
import java.util.UUID;

public interface RoleManagementPort {
    void grant(UUID userId, Role role, SecurityContextData securityContext);

    void revoke(UUID userId, Role role, SecurityContextData securityContext);

    Set<Role> rolesFor(UUID userId);
}
