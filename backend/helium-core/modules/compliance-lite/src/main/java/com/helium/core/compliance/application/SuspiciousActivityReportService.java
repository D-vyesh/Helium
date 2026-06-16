package com.helium.core.compliance.application;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing Suspicious Activity Reports (SAR).
 */
@Service
public class SuspiciousActivityReportService {
    private static final Logger log = LoggerFactory.getLogger(SuspiciousActivityReportService.class);

    public void flagSuspiciousActivity(UUID userId, String activityType, String description) {
        log.warn("FLAGGED SAR [User: {}] [Type: {}]: {}", userId, activityType, description);
        
        // In a real system, this inserts a record into `suspicious_activity_reports`
        // and alerts the Compliance Operations team.
        SarRecord record = new SarRecord(UUID.randomUUID(), userId, activityType, description, Instant.now());
        submitToComplianceQueue(record);
    }

    private void submitToComplianceQueue(SarRecord record) {
        // Publish to internal compliance review queue
        log.info("SAR {} submitted for compliance review.", record.id());
    }

    private record SarRecord(UUID id, UUID userId, String activityType, String description, Instant reportedAt) {}
}
