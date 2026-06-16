package com.helium.core.app.api;

import org.springframework.context.ApplicationEvent;

/**
 * Published when secrets are rotated, allowing downstream components
 * to react (e.g., re-hash API keys, refresh DB connections).
 */
public class SecretRotationEvent extends ApplicationEvent {
    private final String path;
    private final String oldVersion;
    private final String newVersion;

    public SecretRotationEvent(Object source, String path, String oldVersion, String newVersion) {
        super(source);
        this.path = path;
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }

    public String path() {
        return path;
    }

    public String oldVersion() {
        return oldVersion;
    }

    public String newVersion() {
        return newVersion;
    }
}
