package com.helium.core.compliance.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpSanctionsScreeningProviderTest {

    private final HttpSanctionsScreeningProvider provider = new HttpSanctionsScreeningProvider(
        RestClient.builder(),
        "",
        ""
    );

    @Test
    void holdsUserAndAddressChecksWhenProviderCredentialsAreMissing() {
        assertTrue(provider.isUserSanctioned(UUID.randomUUID(), "Example User", "US"));
        assertTrue(provider.isAddressSanctioned("BTC", "bc1example"));
    }
}
