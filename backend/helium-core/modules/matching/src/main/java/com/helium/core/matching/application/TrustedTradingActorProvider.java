package com.helium.core.matching.application;

public interface TrustedTradingActorProvider {
    void requireTradingSystem();

    String tradingActorId();
}
