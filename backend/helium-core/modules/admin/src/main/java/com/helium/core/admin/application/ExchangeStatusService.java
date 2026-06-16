package com.helium.core.admin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExchangeStatusService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeStatusService.class);

    private final Map<String, String> componentStatus = new ConcurrentHashMap<>();

    public ExchangeStatusService() {
        componentStatus.put("matching-engine", "OPERATIONAL");
        componentStatus.put("wallet-custody", "OPERATIONAL");
        componentStatus.put("ledger", "OPERATIONAL");
        componentStatus.put("api-gateway", "OPERATIONAL");
    }

    public void updateComponentStatus(String component, String status) {
        log.warn("STATUS UPDATE: Component {} transitioned to {}", component, status);
        componentStatus.put(component, status);
        
        if ("DEGRADED".equals(status) || "OUTAGE".equals(status)) {
            // Stub: Trigger PagerDuty/Incident response system
            log.error("INCIDENT: Triggering on-call paging for {} outage.", component);
        }
    }

    public Map<String, String> getPublicStatus() {
        return componentStatus;
    }
}
