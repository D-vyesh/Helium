package com.helium.core.admin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SreRunbookAutomation {
    private static final Logger log = LoggerFactory.getLogger(SreRunbookAutomation.class);

    private final ExchangeStatusService exchangeStatusService;

    public SreRunbookAutomation(ExchangeStatusService exchangeStatusService) {
        this.exchangeStatusService = exchangeStatusService;
    }

    public void handleComponentDegradation(String componentName) {
        exchangeStatusService.updateComponentStatus(componentName, "DEGRADED");
        log.error(
            "SRE runbook requested for {}. Automatic failover is not configured; "
                + "an authorized operator must perform and verify recovery before marking it operational.",
            componentName
        );
    }
}
