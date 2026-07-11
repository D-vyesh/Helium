package com.helium.core.wallet.infrastructure.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.infrastructure.rpc.BlockchainProviderPool.RpcProviderState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "helium.wallet.rpc.health-monitor.enabled", havingValue = "true", matchIfMissing = true)
public class BlockchainRpcHealthMonitor {
    private final BlockchainProviderPool providerPool;
    private final RpcProviderHealthPersistence healthPersistence;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BlockchainRpcHealthMonitor(
        BlockchainProviderPool providerPool,
        RpcProviderHealthPersistence healthPersistence,
        ObjectMapper objectMapper
    ) {
        this.providerPool = providerPool;
        this.healthPersistence = healthPersistence;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Scheduled(fixedDelayString = "${helium.wallet.rpc.health-monitor.poll-interval-ms:30000}")
    public void monitorProviders() {
        providerPool.providers().forEach(this::checkProvider);
    }

    private void checkProvider(RpcProviderState provider) {
        Instant start = Instant.now();
        try {
            long latestBlock = switch (provider.chain()) {
                case "BTC" -> bitcoinBlockHeight(provider.url());
                case "ETH" -> ethereumBlockHeight(provider.url());
                case "SOL" -> solanaSlot(provider.url());
                default -> throw new IllegalStateException("unsupported chain " + provider.chain());
            };
            providerPool.recordSuccess(provider, Duration.between(start, Instant.now()), latestBlock);
        } catch (Exception exception) {
            providerPool.recordFailure(provider, exception, Duration.between(start, Instant.now()));
        } finally {
            healthPersistence.save(provider);
        }
    }

    private long bitcoinBlockHeight(String nodeUrl) throws Exception {
        return jsonRpc(nodeUrl, "1.0", "getblockcount", List.of()).asLong();
    }

    private long ethereumBlockHeight(String nodeUrl) throws Exception {
        return quantity(jsonRpc(nodeUrl, "2.0", "eth_blockNumber", List.of()));
    }

    private long solanaSlot(String nodeUrl) throws Exception {
        return jsonRpc(nodeUrl, "2.0", "getSlot", List.of()).asLong();
    }

    private JsonNode jsonRpc(String nodeUrl, String version, String method, List<Object> params) throws Exception {
        URI uri = URI.create(nodeUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(strippedUserInfo(uri))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json");
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            String token = Base64.getEncoder().encodeToString(uri.getUserInfo().getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        var body = objectMapper.createObjectNode();
        body.put("jsonrpc", version);
        body.put("id", "helium-rpc-health");
        body.put("method", method);
        body.set("params", objectMapper.valueToTree(params));
        HttpResponse<String> response = httpClient.send(
            builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() == 429) {
            throw new IllegalStateException("HTTP 429 rate limited by provider Retry-After=" + retryAfterSeconds(response));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("RPC health check failed with HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("error").isNull()) {
            throw new IllegalStateException("RPC health check failed: " + root.path("error"));
        }
        return root.path("result");
    }

    private static URI strippedUserInfo(URI uri) throws Exception {
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
    }

    private static long quantity(JsonNode value) {
        String hex = value.asText();
        if (hex == null || !hex.startsWith("0x")) {
            throw new IllegalStateException("Ethereum RPC returned an invalid quantity");
        }
        return Long.parseUnsignedLong(hex.substring(2), 16);
    }

    private static long retryAfterSeconds(HttpResponse<?> response) {
        return response.headers()
            .firstValue("Retry-After")
            .map(value -> {
                try {
                    return Math.max(1L, Long.parseLong(value.trim()));
                } catch (NumberFormatException exception) {
                    return 60L;
                }
            })
            .orElse(60L);
    }
}
