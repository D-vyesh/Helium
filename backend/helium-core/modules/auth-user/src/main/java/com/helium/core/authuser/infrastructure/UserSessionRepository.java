package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.SessionStatus;
import com.helium.core.authuser.domain.UserSession;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    @Query("select session from UserSession session where session.tokenHash = :tokenHash")
    Optional<UserSession> findReadByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserSession> findAllByUserIdAndStatus(UUID userId, SessionStatus status);
}
