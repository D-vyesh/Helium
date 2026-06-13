package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.SecurityAuditEvent;
import com.helium.core.authuser.domain.SecurityAuditEventType;
import com.helium.core.authuser.infrastructure.SecurityAuditEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {
    private final SecurityAuditEventRepository repository;
    private final TrustedActorProvider trustedActorProvider;
    private final Clock clock;

    public SecurityAuditService(
        SecurityAuditEventRepository repository,
        TrustedActorProvider trustedActorProvider,
        Clock clock
    ) {
        this.repository = repository;
        this.trustedActorProvider = trustedActorProvider;
        this.clock = clock;
    }

    public void record(
        SecurityAuditEventType eventType,
        UUID userId,
        UUID sessionId,
        SecurityContextData context,
        String details
    ) {
        repository.save(SecurityAuditEvent.record(
            eventType,
            userId,
            sessionId,
            trustedActorProvider.currentActorId(),
            context.ipAddress(),
            context.userAgent(),
            details,
            clock.instant()
        ));
    }
}
