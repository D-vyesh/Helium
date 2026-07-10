package com.helium.core.wallet.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HashicorpVaultCustodyProvider implements CustodyProvider {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String address;
    private final String token;

    public HashicorpVaultCustodyProvider(
        ObjectMapper objectMapper,
        @Value("${helium.wallet.custody.vault.address:}") String address,
        @Value("${helium.wallet.custody.vault.token:}") String token
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.address = address == null ? "" : address.strip();
        this.token = token == null ? "" : token.strip();
    }

    @Override
    public SigningResult sign(SigningRequest request) {
        if (!isHealthy()) {
            throw new UnsupportedOperationException("Hashicorp Vault custody signing is not configured");
        }
        Instant start = Instant.now();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "input", request.payloadBase64(),
                "prehashed", request.signingAlgorithm().name().contains("ECDSA")
            ));
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(address + "/v1/transit/sign/" + request.keyAlias()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Vault signing failed with HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String signature = root.path("data").path("signature").asText();
            if (signature == null || signature.isBlank()) {
                throw new IllegalStateException("Vault signing response did not contain a signature");
            }
            return new SigningResult(providerName(), signature, signature, Duration.between(start, Instant.now()));
        } catch (Exception exception) {
            throw new IllegalStateException("Vault signing failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean isHealthy() {
        return !address.isBlank() && !token.isBlank();
    }

    @Override
    public String providerName() {
        return "HASHICORP_VAULT";
    }
}
