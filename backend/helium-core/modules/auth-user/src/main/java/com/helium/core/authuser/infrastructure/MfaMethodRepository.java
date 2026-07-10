package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.MfaMethod;
import com.helium.core.authuser.domain.MfaStatus;
import com.helium.core.authuser.domain.MfaType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MfaMethodRepository extends JpaRepository<MfaMethod, UUID> {
    boolean existsByUserIdAndStatus(UUID userId, MfaStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaMethod> findByUserIdAndType(UUID userId, MfaType type);
}
