package com.helium.core.wallet.infrastructure.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class RpcProviderHealthStateRestorer {
    private static final Logger log = LoggerFactory.getLogger(RpcProviderHealthStateRestorer.class);

    private final BlockchainProviderPool providerPool;
    private final RpcProviderHealthPersistence healthPersistence;

    public RpcProviderHealthStateRestorer(
        BlockchainProviderPool providerPool,
        RpcProviderHealthPersistence healthPersistence
    ) {
        this.providerPool = providerPool;
        this.healthPersistence = healthPersistence;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreManualProviderState() {
        try {
            healthPersistence.loadManualProviderStates().forEach(state ->
                providerPool.applyPersistedManualState(state.providerId(), state.manuallyDisabled(), state.disabledAt()));
        } catch (DataAccessException exception) {
            log.debug("RPC provider health state was not restored; persistence is not ready", exception);
        }
    }
}
