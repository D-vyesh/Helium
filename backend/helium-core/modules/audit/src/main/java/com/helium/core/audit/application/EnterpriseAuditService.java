package com.helium.core.audit.application;

import com.helium.core.audit.domain.ImmutableAuditLog;
import com.helium.core.audit.infrastructure.ImmutableAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseAuditService {
    private static final Logger log = LoggerFactory.getLogger(EnterpriseAuditService.class);

    private final ImmutableAuditLogRepository auditRepository;
    private final Object hashLock = new Object(); // synchronize chaining

    public EnterpriseAuditService(ImmutableAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void logEnterpriseEvent(String eventType, String actorId, String payloadJson) {
        synchronized (hashLock) {
            String previousHash = auditRepository.findLatestLog()
                .map(ImmutableAuditLog::currentHash)
                .orElse("0000000000000000000000000000000000000000000000000000000000000000"); // Genesis hash
            
            ImmutableAuditLog logEntry = new ImmutableAuditLog(eventType, actorId, payloadJson, previousHash);
            auditRepository.save(logEntry);
            log.info("WORM Audit Logged: {} by {}. Hash: {}", eventType, actorId, logEntry.currentHash());
        }
    }
}
