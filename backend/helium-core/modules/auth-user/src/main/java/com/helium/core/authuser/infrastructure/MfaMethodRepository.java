package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.MfaMethod;
import com.helium.core.authuser.domain.MfaStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaMethodRepository extends JpaRepository<MfaMethod, UUID> {
    boolean existsByUserIdAndStatus(UUID userId, MfaStatus status);
}
