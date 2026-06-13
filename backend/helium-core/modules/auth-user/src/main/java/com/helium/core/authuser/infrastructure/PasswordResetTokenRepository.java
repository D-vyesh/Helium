package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PasswordResetToken> findAllByUserIdAndConsumedAtIsNull(UUID userId);
}
