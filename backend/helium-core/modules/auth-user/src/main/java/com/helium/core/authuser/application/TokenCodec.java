package com.helium.core.authuser.application;

public interface TokenCodec {
    TokenValue generate();

    String hash(String rawToken);
}
