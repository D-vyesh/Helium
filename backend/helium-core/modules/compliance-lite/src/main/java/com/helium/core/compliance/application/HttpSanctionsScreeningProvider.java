package com.helium.core.compliance.application;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Provider-neutral HTTP adapter for the exchange's contracted sanctions service.
 * The provider must expose POST /v1/screening/users and /v1/screening/addresses,
 * returning a JSON object with a boolean {@code sanctioned} field. Failures are
 * intentionally treated as sanctions hits so payment flows are held for review.
 */
@Service
public class HttpSanctionsScreeningProvider implements SanctionsScreeningProvider {
    private static final Logger log = LoggerFactory.getLogger(HttpSanctionsScreeningProvider.class);

    private final RestClient client;
    private final boolean configured;

    public HttpSanctionsScreeningProvider(
        RestClient.Builder builder,
        @Value("${helium.compliance.sanctions.base-url}") String baseUrl,
        @Value("${helium.compliance.sanctions.api-token:}") String apiToken
    ) {
        this.configured = !baseUrl.isBlank() && !apiToken.isBlank();
        this.client = configured
            ? builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                    java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                ))
                .build()
            : null;
    }

    @Override
    public boolean isUserSanctioned(UUID userId, String fullName, String countryCode) {
        return screened("user", Map.of(
            "userId", userId.toString(),
            "fullName", fullName == null ? "" : fullName,
            "countryCode", countryCode == null ? "" : countryCode
        ));
    }

    @Override
    public boolean isAddressSanctioned(String asset, String address) {
        return screened("address", Map.of(
            "asset", asset == null ? "" : asset,
            "address", address == null ? "" : address
        ));
    }

    private boolean screened(String subjectType, Map<String, String> payload) {
        if (!configured) {
            log.error("Sanctions provider credentials are not configured; holding {} for review", subjectType);
            return true;
        }
        try {
            ScreeningResponse response = client.post()
                .uri("/v1/screening/" + subjectType + "s")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(ScreeningResponse.class);
            if (response == null) {
                log.error("Sanctions provider returned an empty {} screening response; holding for review", subjectType);
                return true;
            }
            return response.sanctioned();
        } catch (RuntimeException exception) {
            log.error("Sanctions {} screening failed; holding for review", subjectType, exception);
            return true;
        }
    }

    private record ScreeningResponse(boolean sanctioned) {}
}
