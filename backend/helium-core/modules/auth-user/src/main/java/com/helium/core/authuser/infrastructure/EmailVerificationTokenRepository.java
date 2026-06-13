package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailVerificationToken> findAllByUserIdAndConsumedAtIsNull(UUID userId);
}
