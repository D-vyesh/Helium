package com.helium.core.app.api.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "helium.email")
public record EmailProperties(
    String fromAddress,
    String fromName,
    String baseUrl,
    boolean enabled
) {
    public EmailProperties {
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = "noreply@helium.exchange";
        }
        if (fromName == null || fromName.isBlank()) {
            fromName = "HELIUM Exchange";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:3000";
        }
    }
}
