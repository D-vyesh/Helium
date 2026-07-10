package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.TotpSecret;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TotpSecretRepository extends JpaRepository<TotpSecret, UUID> {

    Optional<TotpSecret> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TotpSecret> findForUpdateByUserId(UUID userId);
}
