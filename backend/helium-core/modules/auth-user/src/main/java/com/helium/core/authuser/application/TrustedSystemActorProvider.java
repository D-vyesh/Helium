package com.helium.core.authuser.application;

public interface TrustedSystemActorProvider {
    void requireChainMonitor();

    String chainMonitorActorId();
}
