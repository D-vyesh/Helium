package com.helium.core.audit.infrastructure;

import com.helium.core.audit.domain.ImmutableAuditLog;

/**
 * Port for exporting audit logs and security anomalies to an external SIEM
 * (e.g., Splunk, Datadog, Elastic).
 */
public interface SiemIntegrationPort {
    void pushLog(ImmutableAuditLog log);
    void raiseAnomalyAlert(String alertType, String description);
}
