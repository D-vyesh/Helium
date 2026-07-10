package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.MfaSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MfaSessionRepository extends JpaRepository<MfaSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaSession> findByTokenHash(String tokenHash);
}
