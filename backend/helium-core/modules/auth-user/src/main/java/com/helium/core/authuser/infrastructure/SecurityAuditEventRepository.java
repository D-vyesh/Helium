package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.SecurityAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {
}
