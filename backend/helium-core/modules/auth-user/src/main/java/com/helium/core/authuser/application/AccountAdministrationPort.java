package com.helium.core.authuser.application;

import java.util.UUID;

public interface AccountAdministrationPort {
    void suspend(UUID userId, SecurityContextData securityContext);

    void reactivate(UUID userId, SecurityContextData securityContext);

    void unlock(UUID userId, SecurityContextData securityContext);
}
