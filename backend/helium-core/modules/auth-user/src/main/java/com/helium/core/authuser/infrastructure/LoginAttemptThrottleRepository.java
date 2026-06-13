package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.LoginAttemptThrottle;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface LoginAttemptThrottleRepository extends JpaRepository<LoginAttemptThrottle, UUID> {
    Optional<LoginAttemptThrottle> findReadBySubjectHashAndSourceHash(String subjectHash, String sourceHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginAttemptThrottle> findBySubjectHashAndSourceHash(String subjectHash, String sourceHash);

    void deleteBySubjectHashAndSourceHash(String subjectHash, String sourceHash);
}
