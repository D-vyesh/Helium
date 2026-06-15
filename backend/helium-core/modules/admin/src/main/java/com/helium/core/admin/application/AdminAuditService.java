package com.helium.core.admin.application;

import com.helium.core.admin.domain.AdminAuditAction;
import com.helium.core.admin.domain.AdminAuditEvent;
import com.helium.core.admin.infrastructure.AdminAuditEventRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private final AdminAuditEventRepository repository;
    private final Clock clock;

    public AdminAuditService(AdminAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(AdminAuditAction action, String actorId, String targetType, String targetId, String details) {
        repository.save(AdminAuditEvent.record(action, actorId, targetType, targetId, details, clock.instant()));
    }
}
