package com.helium.core.wallet.infrastructure.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lightweight JSON-RPC client for Solana.
 */
@Component
public class SolanaRpcClient {
    private static final Logger log = LoggerFactory.getLogger(SolanaRpcClient.class);

    private final CircuitBreakerClient circuitBreaker;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SolanaRpcClient(CircuitBreakerClient circuitBreaker, ObjectMapper objectMapper) {
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public String getRecentBlockhash() {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            log.debug("Executing getLatestBlockhash on node {}", nodeUrl);
            return rpc(nodeUrl, "getLatestBlockhash", List.of()).path("value").path("blockhash").asText();
        }, 1_000);
    }

    public long getSlot() {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> rpc(nodeUrl, "getSlot", List.of()).asLong(), 1_000);
    }

    public String sendTransaction(byte[] signedTx) {
        return sendTransaction(signedTx, false, "processed");
    }

    public String sendTransaction(byte[] signedTx, boolean skipPreflight, String commitment) {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            log.debug("Executing sendTransaction on node {}", nodeUrl);
            String encoded = Base64.getEncoder().encodeToString(signedTx);
            return rpc(nodeUrl, "sendTransaction", List.of(encoded, Map.of(
                "encoding", "base64",
                "skipPreflight", skipPreflight,
                "preflightCommitment", requireCommitment(commitment),
                "maxRetries", 3
            ))).asText();
        }, 1_000);
    }

    public long getConfirmations(String signature) {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            JsonNode status = rpc(nodeUrl, "getSignatureStatuses", List.of(
                List.of(signature),
                Map.of("searchTransactionHistory", true)
            )).path("value").path(0);
            if (status.isMissingNode() || status.isNull()) {
                return 0L;
            }
            if (!status.path("err").isNull()) {
                return -1L;
            }
            JsonNode confirmations = status.path("confirmations");
            if (confirmations.isNumber()) {
                return confirmations.asLong();
            }
            return "finalized".equals(status.path("confirmationStatus").asText()) ? 32L : 0L;
        }, 1_000);
    }

    public List<DetectedDeposit> scanSlotRange(long startSlot, long endSlot, Map<String, String> watchedAddresses) {
        if (endSlot < startSlot || watchedAddresses.isEmpty()) {
            return List.of();
        }
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            List<DetectedDeposit> deposits = new ArrayList<>();
            for (String address : watchedAddresses.values()) {
                JsonNode signatures = rpc(nodeUrl, "getSignaturesForAddress", List.<Object>of(address, Map.of("limit", 1_000)));
                if (!signatures.isArray()) {
                    continue;
                }
                for (JsonNode signatureInfo : signatures) {
                    long slot = signatureInfo.path("slot").asLong(-1L);
                    if (slot > endSlot) {
                        continue;
                    }
                    if (slot < startSlot) {
                        break;
                    }
                    if (!signatureInfo.path("err").isNull()) {
                        continue;
                    }
                    addNativeSolDeposit(nodeUrl, deposits, address, signatureInfo.path("signature").asText());
                }
            }
            return deposits;
        }, 2_000);
    }

    public BigDecimal estimatePriorityFeeLamports() {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            JsonNode fees = rpc(nodeUrl, "getRecentPrioritizationFees", List.of());
            if (!fees.isArray() || fees.isEmpty()) {
                return BigDecimal.ZERO;
            }
            BigInteger total = BigInteger.ZERO;
            int count = 0;
            for (JsonNode fee : fees) {
                total = total.add(BigInteger.valueOf(fee.path("prioritizationFee").asLong()));
                count++;
            }
            return new BigDecimal(total).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
        }, 1_000);
    }

    /** Returns the validator-calculated fee for an unsigned, compiled transaction message. */
    public BigDecimal getFeeForMessage(byte[] compiledMessage) {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            String encodedMessage = Base64.getEncoder().encodeToString(compiledMessage);
            JsonNode value = rpc(nodeUrl, "getFeeForMessage", List.<Object>of(encodedMessage, Map.of(
                "commitment", "processed"
            ))).path("value");
            if (value.isNull() || !value.canConvertToLong()) {
                throw new IllegalStateException("Solana RPC getFeeForMessage returned no fee");
            }
            return BigDecimal.valueOf(value.asLong()).movePointLeft(9);
        }, 1_000);
    }

    public BigDecimal getBalance(String address) {
        return circuitBreaker.executeWithHedging("SOL", nodeUrl -> {
            JsonNode value = rpc(nodeUrl, "getBalance", List.<Object>of(address, Map.of(
                "commitment", "processed"
            ))).path("value");
            if (!value.canConvertToLong()) {
                throw new IllegalStateException("Solana RPC getBalance returned an invalid balance");
            }
            return BigDecimal.valueOf(value.asLong()).movePointLeft(9);
        }, 1_000);
    }

    private void addNativeSolDeposit(String nodeUrl, List<DetectedDeposit> deposits, String address, String signature) throws Exception {
        JsonNode transaction = rpc(nodeUrl, "getTransaction", List.<Object>of(signature, Map.of(
            "encoding", "jsonParsed",
            "maxSupportedTransactionVersion", 0
        )));
        if (transaction.isMissingNode() || transaction.isNull()) {
            return;
        }
        JsonNode keys = transaction.path("transaction").path("message").path("accountKeys");
        JsonNode preBalances = transaction.path("meta").path("preBalances");
        JsonNode postBalances = transaction.path("meta").path("postBalances");
        for (int i = 0; i < keys.size() && i < preBalances.size() && i < postBalances.size(); i++) {
            String pubkey = keys.path(i).path("pubkey").asText();
            if (!address.equals(pubkey)) {
                continue;
            }
            long delta = postBalances.path(i).asLong() - preBalances.path(i).asLong();
            if (delta <= 0) {
                return;
            }
            BigDecimal amount = BigDecimal.valueOf(delta).movePointLeft(9).stripTrailingZeros();
            deposits.add(new DetectedDeposit("SOL", signature, i, amount, "SOL", address));
            return;
        }
    }

    private JsonNode rpc(String nodeUrl, String method, List<Object> params) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", "helium-wallet");
        body.put("method", method);
        body.set("params", objectMapper.valueToTree(params));
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(URI.create(nodeUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Solana RPC " + method + " failed with HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("error").isNull()) {
            throw new IllegalStateException("Solana RPC " + method + " failed: " + root.path("error"));
        }
        return root.path("result");
    }

    private static String requireCommitment(String commitment) {
        if (commitment == null || commitment.isBlank()) {
            return "processed";
        }
        return switch (commitment.trim()) {
            case "processed", "confirmed", "finalized" -> commitment.trim();
            default -> throw new IllegalArgumentException("unsupported Solana commitment");
        };
    }
}
