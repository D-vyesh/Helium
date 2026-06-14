package com.helium.core.marketdata.application;

public interface WebSocketBroadcaster {
    void broadcast(String topic, Object payload);
}
