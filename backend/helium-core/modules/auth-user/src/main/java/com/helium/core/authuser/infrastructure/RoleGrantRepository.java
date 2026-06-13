package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.RoleGrant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RoleGrantRepository extends JpaRepository<RoleGrant, UUID> {
    List<RoleGrant> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoleGrant> findByUserIdAndRoleAndRevokedAtIsNull(UUID userId, Role role);

    boolean existsByUserIdAndRoleAndRevokedAtIsNull(UUID userId, Role role);
}
