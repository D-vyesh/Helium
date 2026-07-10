package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.TotpBackupCode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TotpBackupCodeRepository extends JpaRepository<TotpBackupCode, UUID> {

    List<TotpBackupCode> findAllByUserIdAndUsedAtIsNull(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TotpBackupCode> findByUserIdAndCodeHash(UUID userId, String codeHash);

    @Modifying
    @Query("delete from TotpBackupCode b where b.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
