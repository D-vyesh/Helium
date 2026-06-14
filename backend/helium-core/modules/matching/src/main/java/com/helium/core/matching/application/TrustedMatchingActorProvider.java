package com.helium.core.matching.application;

public interface TrustedMatchingActorProvider {
    void requireMatchingEngine();

    String matchingActorId();
}
