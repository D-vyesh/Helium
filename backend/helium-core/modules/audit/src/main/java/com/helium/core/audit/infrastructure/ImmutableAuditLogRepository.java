package com.helium.core.audit.infrastructure;

import com.helium.core.audit.domain.ImmutableAuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ImmutableAuditLogRepository extends JpaRepository<ImmutableAuditLog, UUID> {
    @Query("SELECT l FROM ImmutableAuditLog l ORDER BY l.createdAt DESC LIMIT 1")
    Optional<ImmutableAuditLog> findLatestLog();
}
