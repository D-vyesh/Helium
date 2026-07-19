package com.helium.core.wallet.infrastructure.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.application.BlockchainTransactionObservation;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** RPC-only EIP-1559 data source. It neither signs nor submits transactions. */
@Component
public class EthereumRpcClient {
    private final CircuitBreakerClient circuitBreaker;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EthereumRpcClient(CircuitBreakerClient circuitBreaker, ObjectMapper objectMapper) {
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Eip1559BuildData transactionData(String from, String to, BigInteger valueWei) {
        return circuitBreaker.executeWithHedging("ETH", nodeUrl -> {
            BigInteger chainId = quantity(rpc(nodeUrl, "eth_chainId", List.of()));
            BigInteger nonce = quantity(rpc(nodeUrl, "eth_getTransactionCount", List.of(from, "pending")));
            JsonNode feeHistory = rpc(nodeUrl, "eth_feeHistory", List.of("0x5", "latest", List.of(50)));
            JsonNode baseFees = feeHistory.path("baseFeePerGas");
            if (!baseFees.isArray() || baseFees.isEmpty()) {
                throw new IllegalStateException("eth_feeHistory returned no base fee");
            }
            BigInteger baseFee = quantity(baseFees.path(baseFees.size() - 1));
            BigInteger priorityFee = quantity(rpc(nodeUrl, "eth_maxPriorityFeePerGas", List.of()));
            BigInteger maxFee = baseFee.multiply(BigInteger.TWO).add(priorityFee);
            JsonNode gas = rpc(nodeUrl, "eth_estimateGas", List.of(Map.of(
                "from", from,
                "to", to,
                "value", hex(valueWei),
                "data", "0x"
            )));
            BigInteger gasLimit = quantity(gas);
            return new Eip1559BuildData(chainId, nonce, gasLimit, maxFee, priorityFee);
        }, 2_000);
    }

    public BigInteger getBalanceWei(String address) {
        return circuitBreaker.executeWithHedging("ETH", nodeUrl ->
            quantity(rpc(nodeUrl, "eth_getBalance", List.of(address, "pending"))), 1_000);
    }

    public String sendRawTransaction(String signedRawTransactionHex) {
        String payload = requireHex(signedRawTransactionHex, "signedRawTransactionHex");
        return circuitBreaker.executeWithFailover("ETH", nodeUrl ->
            rpc(nodeUrl, "eth_sendRawTransaction", List.of(payload)).asText());
    }

    public long getConfirmations(String txHash) {
        return circuitBreaker.executeWithHedging("ETH", nodeUrl -> {
            JsonNode receipt = rpc(nodeUrl, "eth_getTransactionReceipt", List.of(requireHex(txHash, "txHash")));
            if (receipt.isNull() || receipt.isMissingNode()) {
                JsonNode transaction = rpc(nodeUrl, "eth_getTransactionByHash", List.of(txHash));
                return transaction.isNull() || transaction.isMissingNode() ? -1L : 0L;
            }
            if ("0x0".equalsIgnoreCase(receipt.path("status").asText())) {
                return -1L;
            }
            JsonNode blockNumber = receipt.path("blockNumber");
            if (blockNumber.isMissingNode() || blockNumber.isNull()) {
                return 0L;
            }
            BigInteger latest = quantity(rpc(nodeUrl, "eth_blockNumber", List.of()));
            BigInteger included = quantity(blockNumber);
            return latest.subtract(included).add(BigInteger.ONE).max(BigInteger.ZERO).longValue();
        }, 1_000);
    }

    public BlockchainTransactionObservation observeTransaction(String nodeUrl, String providerId, String txHash, Instant observedAt) throws Exception {
        String hash = requireHex(txHash, "txHash");
        JsonNode receipt = rpc(nodeUrl, "eth_getTransactionReceipt", List.of(hash));
        if (receipt.isNull() || receipt.isMissingNode()) {
            JsonNode transaction = rpc(nodeUrl, "eth_getTransactionByHash", List.of(hash));
            boolean observed = !transaction.isNull() && !transaction.isMissingNode();
            return new BlockchainTransactionObservation(
                "ETH",
                txHash,
                providerId,
                observed,
                false,
                null,
                null,
                observed ? 0L : null,
                observed ? "PENDING" : "NOT_FOUND",
                observedAt
            );
        }
        String status = receipt.path("status").asText();
        boolean success = !"0x0".equalsIgnoreCase(status);
        JsonNode blockNumber = receipt.path("blockNumber");
        Long includedAt = blockNumber.isMissingNode() || blockNumber.isNull() ? null : quantity(blockNumber).longValue();
        Long confirmations = null;
        if (includedAt != null) {
            BigInteger latest = quantity(rpc(nodeUrl, "eth_blockNumber", List.of()));
            confirmations = latest.subtract(BigInteger.valueOf(includedAt)).add(BigInteger.ONE).max(BigInteger.ZERO).longValue();
        }
        return new BlockchainTransactionObservation(
            "ETH",
            txHash,
            providerId,
            true,
            success && includedAt != null,
            includedAt,
            receipt.path("blockHash").isTextual() ? receipt.path("blockHash").asText() : null,
            confirmations,
            success ? "SUCCESS" : "REVERTED",
            observedAt
        );
    }

    public CanonicalBlockReference getCanonicalBlock(long height) {
        return circuitBreaker.executeWithFailover("ETH", nodeUrl -> {
            JsonNode block = rpc(nodeUrl, "eth_getBlockByNumber", List.of(hex(BigInteger.valueOf(height)), false));
            if (block.isMissingNode() || block.isNull()) {
                throw new IllegalStateException("Ethereum block was not found at height " + height);
            }
            return new CanonicalBlockReference(
                "ETH",
                height,
                block.path("hash").asText(),
                block.path("parentHash").isTextual() ? block.path("parentHash").asText() : null
            );
        });
    }

    private JsonNode rpc(String nodeUrl, String method, List<Object> params) throws Exception {
        var request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "helium-wallet");
        request.put("method", method);
        request.set("params", objectMapper.valueToTree(params));
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(URI.create(nodeUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Ethereum RPC " + method + " failed with HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new IllegalStateException("Ethereum RPC " + method + " failed: " + error);
        }
        return root.path("result");
    }

    private static BigInteger quantity(JsonNode value) {
        String hex = value.asText();
        if (hex == null || !hex.startsWith("0x")) {
            throw new IllegalStateException("Ethereum RPC returned an invalid quantity");
        }
        return new BigInteger(hex.substring(2), 16);
    }

    private static String hex(BigInteger value) {
        return "0x" + value.toString(16);
    }

    private static String requireHex(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String hex = value.trim();
        if (!hex.startsWith("0x")) {
            throw new IllegalArgumentException(field + " must be 0x-prefixed hex");
        }
        return hex;
    }

    public record Eip1559BuildData(
        BigInteger chainId,
        BigInteger nonce,
        BigInteger gasLimit,
        BigInteger maxFeePerGas,
        BigInteger maxPriorityFeePerGas
    ) {}
}
