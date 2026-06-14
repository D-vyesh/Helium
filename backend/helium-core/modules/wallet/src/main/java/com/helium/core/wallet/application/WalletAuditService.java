package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.WalletAuditEvent;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.infrastructure.WalletAuditEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WalletAuditService {
    private final WalletAuditEventRepository repository;
    private final Clock clock;

    public WalletAuditService(WalletAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(WalletAuditEventType eventType, UUID aggregateId, String actorId, String details) {
        repository.save(WalletAuditEvent.record(eventType, aggregateId, actorId, details, clock.instant()));
    }
}

