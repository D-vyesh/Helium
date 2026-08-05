package com.helium.core.admin.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"chaos", "test"})
public class ChaosFaultInjectorService {
    private static final Logger log = LoggerFactory.getLogger(ChaosFaultInjectorService.class);

    private final ExchangeStatusService exchangeStatusService;
    private final SreRunbookAutomation sreRunbookAutomation;

    public ChaosFaultInjectorService(ExchangeStatusService exchangeStatusService, SreRunbookAutomation sreRunbookAutomation) {
        this.exchangeStatusService = exchangeStatusService;
        this.sreRunbookAutomation = sreRunbookAutomation;
    }

    public void injectRedisNetworkPartition() {
        log.warn("CHAOS INJECTION: Simulating Redis network partition. Failing connections.");
        // Simulate outage
        exchangeStatusService.updateComponentStatus("matching-engine", "DEGRADED");
        
        // SRE Automation kicks in
        sreRunbookAutomation.handleComponentDegradation("matching-engine");
    }

    public void injectRpcProviderTimeout() {
        log.warn("CHAOS INJECTION: Simulating Ethereum Mainnet RPC provider timeout.");
        exchangeStatusService.updateComponentStatus("wallet-custody", "DEGRADED");
        
        // SRE Automation kicks in
        sreRunbookAutomation.handleComponentDegradation("wallet-custody");
    }
}
