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
        componentStatus.put("matching-engine", "UNKNOWN");
        componentStatus.put("wallet-custody", "UNKNOWN");
        componentStatus.put("ledger", "UNKNOWN");
        componentStatus.put("api-gateway", "UNKNOWN");
    }

    public void updateComponentStatus(String component, String status) {
        log.warn("STATUS UPDATE: Component {} transitioned to {}", component, status);
        componentStatus.put(component, status);
        
        if ("DEGRADED".equals(status) || "OUTAGE".equals(status)) {
            log.error("INCIDENT: {} requires on-call notification; no paging provider is configured", component);
        }
    }

    public Map<String, String> getPublicStatus() {
        return Map.copyOf(componentStatus);
    }
}
