package com.helium.core.wallet.infrastructure.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.wallet.application.BlockchainTransactionObservation;
import com.helium.core.wallet.infrastructure.rpc.CircuitBreakerClient;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lightweight JSON-RPC client for Bitcoin Core.
 */
@Component
public class BitcoinRpcClient {
    private static final Logger log = LoggerFactory.getLogger(BitcoinRpcClient.class);
    private static final BigDecimal MIN_NATIVE_SEGWIT_DUST_BTC = new BigDecimal("0.00000294");

    private final CircuitBreakerClient circuitBreaker;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BitcoinRpcClient(CircuitBreakerClient circuitBreaker, ObjectMapper objectMapper) {
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public String getNewAddress() {
        return circuitBreaker.executeWithHedging("BTC", nodeUrl -> {
            log.debug("Executing getnewaddress on node {}", nodeUrl);
            return rpc(nodeUrl, "getnewaddress", List.of("", "bech32")).asText();
        }, 150);
    }

    public long getBlockCount() {
        return circuitBreaker.executeLatestBlockWithHedging("BTC", nodeUrl -> rpc(nodeUrl, "getblockcount", List.of()).asLong(), 1_000);
    }

    public String sendRawTransaction(byte[] signedTx) {
        return circuitBreaker.executeWithFailover("BTC", nodeUrl -> {
            log.debug("Executing sendrawtransaction on node {}", nodeUrl);
            return rpc(nodeUrl, "sendrawtransaction", List.of(rawTransactionHex(signedTx))).asText();
        });
    }

    public FinalizedPsbt finalizePsbt(String signedPsbt) {
        return circuitBreaker.executeWithFailover("BTC", nodeUrl -> {
            JsonNode result = rpc(nodeUrl, "finalizepsbt", List.<Object>of(
                BlockchainRpcText.require(signedPsbt, "signedPsbt"),
                true
            ));
            boolean complete = result.path("complete").asBoolean(false);
            if (!complete) {
                throw new IllegalStateException("Bitcoin Core did not finalize every PSBT input");
            }
            String hex = result.path("hex").asText();
            if (hex.isBlank()) {
                throw new IllegalStateException("Bitcoin Core did not return finalized transaction hex");
            }
            return new FinalizedPsbt(hex);
        });
    }

    public long getConfirmations(String txHash) {
        try {
            return circuitBreaker.executeWithHedging("BTC", nodeUrl -> {
                JsonNode result = rpc(nodeUrl, "getrawtransaction", List.<Object>of(txHash, true));
                if (result.path("confirmations").isMissingNode() || result.path("confirmations").isNull()) {
                    return 0L;
                }
                return result.path("confirmations").asLong();
            }, 1_000);
        } catch (BitcoinRpcException exception) {
            if (exception.code() == -5) {
                return -1L;
            }
            throw exception;
        }
    }

    public BlockchainTransactionObservation observeTransaction(String nodeUrl, String providerId, String txHash, Instant observedAt) throws Exception {
        try {
            JsonNode result = rpc(nodeUrl, "getrawtransaction", List.<Object>of(txHash, true));
            boolean hasBlock = result.path("blockhash").isTextual();
            Long confirmations = result.path("confirmations").isNumber() ? result.path("confirmations").asLong() : 0L;
            return new BlockchainTransactionObservation(
                "BTC",
                txHash,
                providerId,
                true,
                hasBlock && confirmations >= 0,
                null,
                hasBlock ? result.path("blockhash").asText() : null,
                confirmations,
                result.path("confirmations").isNumber() && confirmations > 0 ? "CONFIRMED" : "MEMPOOL",
                observedAt
            );
        } catch (BitcoinRpcException exception) {
            if (exception.code() == -5) {
                return new BlockchainTransactionObservation("BTC", txHash, providerId, false, false, null, null, null, "NOT_FOUND", observedAt);
            }
            throw exception;
        }
    }

    public List<DetectedDeposit> scanBlockRange(long startBlock, long endBlock, Map<String, String> watchedAddresses) {
        if (endBlock < startBlock || watchedAddresses.isEmpty()) {
            return List.of();
        }
        return circuitBreaker.executeWithFailover("BTC", nodeUrl -> {
            List<DetectedDeposit> deposits = new ArrayList<>();
            for (long height = startBlock; height <= endBlock; height++) {
                String blockHash = rpc(nodeUrl, "getblockhash", List.<Object>of(height)).asText();
                JsonNode block = rpc(nodeUrl, "getblock", List.<Object>of(blockHash, 2));
                for (JsonNode transaction : block.path("tx")) {
                    String txHash = transaction.path("txid").asText();
                    for (JsonNode output : transaction.path("vout")) {
                        String address = outputAddress(output.path("scriptPubKey"));
                        String watchedAddress = watchedAddresses.get(normalizeAddress(address));
                        if (watchedAddress == null) {
                            continue;
                        }
                        BigDecimal amount = output.path("value").decimalValue().stripTrailingZeros();
                        if (amount.signum() <= 0) {
                            continue;
                        }
                        deposits.add(new DetectedDeposit(
                            "BTC",
                            txHash,
                            output.path("n").asInt(),
                            amount,
                            "BTC",
                            watchedAddress
                        ));
                    }
                }
            }
            return deposits;
        });
    }

    public CanonicalBlockReference getCanonicalBlock(long height) {
        return circuitBreaker.executeWithFailover("BTC", nodeUrl -> {
            String blockHash = rpc(nodeUrl, "getblockhash", List.<Object>of(height)).asText();
            JsonNode block = rpc(nodeUrl, "getblock", List.<Object>of(blockHash, 1));
            return new CanonicalBlockReference(
                "BTC",
                height,
                blockHash,
                block.path("previousblockhash").isTextual() ? block.path("previousblockhash").asText() : null
            );
        });
    }

    public BigDecimal estimateFeeBtcPerKilobyte(int targetBlocks) {
        return circuitBreaker.executeWithHedging("BTC", nodeUrl -> {
            JsonNode result = rpc(nodeUrl, "estimatesmartfee", List.<Object>of(targetBlocks));
            if (result.path("feerate").isMissingNode() || result.path("feerate").isNull()) {
                throw new IllegalStateException("Bitcoin fee estimator unavailable: " + result.path("errors"));
            }
            return result.path("feerate").decimalValue();
        }, 1_000);
    }

    /**
     * Builds an unsigned, funded PSBT in the configured Bitcoin Core wallet. Bitcoin Core performs
     * real UTXO selection, change creation, fee calculation, locktime assignment, and RBF sequences.
     */
    public FundedPsbt createFundedPsbt(String destinationAddress, BigDecimal amount, BigDecimal feeRateBtcPerKilobyte, long lockTime) {
        if (amount.compareTo(MIN_NATIVE_SEGWIT_DUST_BTC) < 0) {
            throw new IllegalArgumentException("BTC withdrawal amount is below the native SegWit dust threshold");
        }
        if (lockTime < 0 || lockTime > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("BTC locktime is out of range");
        }
        if (feeRateBtcPerKilobyte == null || feeRateBtcPerKilobyte.signum() <= 0) {
            throw new IllegalArgumentException("BTC fee rate must be positive");
        }
        return circuitBreaker.executeWithFailover("BTC", nodeUrl -> {
            Map<String, Object> options = Map.of(
                "add_inputs", true,
                "includeWatching", true,
                "replaceable", true,
                "change_type", "bech32",
                "fee_rate", feeRateBtcPerKilobyte
            );
            JsonNode result = rpc(nodeUrl, "walletcreatefundedpsbt", List.<Object>of(
                List.of(),
                Map.of(destinationAddress, amount),
                lockTime,
                options,
                true
            ));
            String psbt = result.path("psbt").asText();
            if (psbt.isBlank()) {
                throw new IllegalStateException("Bitcoin Core did not return a PSBT");
            }
            return new FundedPsbt(
                psbt,
                result.path("fee").decimalValue(),
                result.path("changepos").asInt(-1),
                lockTime
            );
        });
    }

    private JsonNode rpc(String nodeUrl, String method, List<Object> params) throws Exception {
        URI uri = URI.create(nodeUrl);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(strippedUserInfo(uri))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json");
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            String token = Base64.getEncoder().encodeToString(uri.getUserInfo().getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + token);
        }
        var body = objectMapper.createObjectNode();
        body.put("jsonrpc", "1.0");
        body.put("id", "helium-wallet");
        body.put("method", method);
        body.set("params", objectMapper.valueToTree(params));
        HttpResponse<String> response = httpClient.send(
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bitcoin RPC " + method + " failed with HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new BitcoinRpcException(method, error.path("code").asInt(Integer.MIN_VALUE), error.toString());
        }
        return root.path("result");
    }

    private static URI strippedUserInfo(URI uri) throws Exception {
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
    }

    private static String rawTransactionHex(byte[] signedTx) {
        if (signedTx == null || signedTx.length == 0) {
            throw new IllegalArgumentException("signedTx is required");
        }
        String ascii = new String(signedTx, StandardCharsets.US_ASCII).trim();
        if (ascii.matches("(?i)^[0-9a-f]+$")) {
            return ascii;
        }
        throw new IllegalArgumentException("BTC signed transaction must be raw transaction hex");
    }

    private static String outputAddress(JsonNode scriptPubKey) {
        if (scriptPubKey.path("address").isTextual()) {
            return scriptPubKey.path("address").asText();
        }
        JsonNode addresses = scriptPubKey.path("addresses");
        if (addresses.isArray() && !addresses.isEmpty()) {
            return addresses.path(0).asText();
        }
        return null;
    }

    private static String normalizeAddress(String address) {
        return address == null ? null : address.trim().toLowerCase(Locale.ROOT);
    }

    private static final class BitcoinRpcException extends IllegalStateException {
        private final int code;

        private BitcoinRpcException(String method, int code, String details) {
            super("Bitcoin RPC " + method + " failed: " + details);
            this.code = code;
        }

        private int code() {
            return code;
        }
    }

    public record FundedPsbt(String psbt, BigDecimal fee, int changePosition, long lockTime) {}

    public record FinalizedPsbt(String rawTransactionHex) {}

    private static final class BlockchainRpcText {
        private BlockchainRpcText() {
        }

        private static String require(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return value.trim();
        }
    }
}
