package com.helium.core.admin.infrastructure;

import com.helium.core.admin.domain.AdminAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {
}
