package com.helium.core.authuser.application;

import java.util.Objects;

public record SecurityContextData(String ipAddress, String userAgent, String deviceInfo) {
    public SecurityContextData(String ipAddress, String userAgent) {
        this(ipAddress, userAgent, userAgent);
    }

    public SecurityContextData {
        ipAddress = requireText(ipAddress, "ipAddress");
        userAgent = requireText(userAgent, "userAgent");
        deviceInfo = requireText(deviceInfo, "deviceInfo");
    }

    public static SecurityContextData system() {
        return new SecurityContextData("internal", "helium-core");
    }

    private static String requireText(String value, String field) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }
}
