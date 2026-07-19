package com.helium.core.wallet.certification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.authuser.CapturingEmailService;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.domain.Role;
import com.helium.core.wallet.application.AddressPort;
import com.helium.core.wallet.application.AssetPort;
import com.helium.core.wallet.application.AssignDepositAddressCommand;
import com.helium.core.wallet.application.RegisterAssetCommand;
import com.helium.core.wallet.application.RegisterNetworkCommand;
import com.helium.core.wallet.infrastructure.blockchain.ChainMonitorJob;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = HeliumCoreApplication.class)
abstract class AbstractBlockchainCertificationIntegrationTest {
    static final SecurityContextData CONTEXT = new SecurityContextData("127.0.0.1", "blockchain-certification");
    private static final String PASSWORD = "Certification-password-123";
    private static final ObjectMapper RPC_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Autowired
    RegistrationPort registrationPort;

    @Autowired
    EmailVerificationPort emailVerificationPort;

    @Autowired
    AssetPort assetPort;

    @Autowired
    AddressPort addressPort;

    @Autowired
    ChainMonitorJob chainMonitorJob;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void certificationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + cert("CERT_POSTGRES_PORT", "55432")
            + "/" + cert("CERT_POSTGRES_DB", "helium"));
        registry.add("spring.datasource.username", () -> cert("CERT_POSTGRES_USER", "helium"));
        registry.add("spring.datasource.password", () -> cert("CERT_POSTGRES_PASSWORD", "helium-cert"));
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> cert("CERT_REDIS_PORT", "56379"));
        registry.add("spring.data.redis.password", () -> cert("CERT_REDIS_PASSWORD", "helium-cert-redis"));
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("helium.market-data.live.enabled", () -> "false");
        registry.add("helium.wallet.rpc.btc.nodes", () -> bitcoinNodeUrl());
        registry.add("helium.wallet.rpc.eth.nodes", () -> "http://localhost:" + cert("CERT_ETH_RPC_PORT", "18545"));
        registry.add("helium.wallet.rpc.sol.nodes", () -> "http://localhost:" + cert("CERT_SOL_RPC_PORT", "18899"));
        registry.add("helium.wallet.blockchain.consensus.minimum-healthy-providers", () -> "1");
        registry.add("helium.wallet.blockchain.consensus.minimum-agreement", () -> "1");
        registry.add("helium.wallet.chain-monitor.enabled", () -> "true");
        registry.add("helium.wallet.builder-worker.enabled", () -> "false");
        registry.add("helium.custody.signing-worker.enabled", () -> "false");
        registry.add("helium.wallet.broadcast-worker.enabled", () -> "false");
        registry.add("helium.wallet.confirmation-worker.enabled", () -> "false");
    }

    @BeforeEach
    void resetCertificationDatabase() {
        SecurityContextHolder.clearContext();
        CapturingEmailService.clear();
        jdbcTemplate.execute("""
            truncate table
                blockchain_consistency_incidents,
                blockchain_canonical_blocks,
                wallet_rpc_provider_health,
                wallet_custody_signing_audit,
                wallet_signed_transactions,
                wallet_unsigned_transactions,
                wallet_custody_keys,
                wallet_blockchain_broadcasts,
                wallet_withdrawal_queue_transitions,
                wallet_withdrawal_queue,
                wallet_withdrawal_authorizations,
                wallet_audit_events,
                wallet_reconciliation_discrepancies,
                wallet_chain_monitor_states,
                wallet_chain_transaction_observations,
                wallet_broadcast_attempts,
                wallet_withdrawals,
                wallet_deposits,
                wallet_deposit_addresses,
                wallet_blockchain_networks,
                wallet_assets,
                ledger_idempotency_records,
                ledger_posting_lines,
                ledger_transactions,
                ledger_balance_snapshots,
                ledger_accounts,
                auth_security_audit_events,
                auth_login_attempt_throttles,
                auth_mfa_methods,
                auth_password_reset_tokens,
                auth_email_verification_tokens,
                auth_role_grants,
                auth_user_sessions,
                auth_credentials,
                auth_user_accounts
            cascade
            """);
        configureNativeAssets();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    UUID activeUser(String email) {
        SecurityContextHolder.clearContext();
        RegistrationResult result = registrationPort.register(new RegistrationCommand(email, "Certification User", PASSWORD, CONTEXT));
        emailVerificationPort.verify(CapturingEmailService.verificationToken(email), CONTEXT);
        return result.userId();
    }

    void assignDepositAddress(UUID userId, String assetCode, String networkCode, String address) {
        authenticateAs(userId);
        addressPort.assignDepositAddress(new AssignDepositAddressCommand(assetCode, networkCode, address, null));
    }

    void pollChains() {
        chainMonitorJob.pollNetworks();
    }

    void primeMonitor(String networkCode) {
        pollChains();
        assertThat(countRows("wallet_chain_monitor_states", "network_code = '" + networkCode + "'")).isEqualTo(1);
    }

    void awaitPostedDeposit(String networkCode, String txHash) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            pollChains();
            String status = nullableString(
                "select status from wallet_deposits where network_code = ? and tx_hash = ?",
                networkCode,
                txHash
            );
            if ("POSTED_TO_LEDGER".equals(status)) {
                return;
            }
            Thread.sleep(1_000);
        }
        throw new AssertionError("deposit was not posted to ledger for " + networkCode + " tx " + txHash);
    }

    BigDecimal balance(String ownerType, String ownerId, String assetCode, String balanceType) {
        BigDecimal value = jdbcTemplate.queryForObject(
            """
            select snapshot.current_balance
            from ledger_balance_snapshots snapshot
            join ledger_accounts account on account.id = snapshot.account_id
            where account.owner_type = ?
              and account.owner_id = ?
              and account.asset_code = ?
              and account.balance_type = ?
            """,
            BigDecimal.class,
            ownerType,
            ownerId,
            assetCode,
            balanceType
        );
        return value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0L : count;
    }

    long countRows(String tableName, String whereClause) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Long.class);
        return count == null ? 0L : count;
    }

    String nullableString(String sql, Object... args) {
        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    JsonNode btc(String method, List<Object> params) {
        return rpc(bitcoinNodeUrl(), method, params);
    }

    JsonNode btcWallet(String method, List<Object> params) {
        ensureBitcoinWallet();
        return rpc(bitcoinWalletUrl(), method, params);
    }

    JsonNode eth(String method, List<Object> params) {
        return rpc("http://localhost:" + cert("CERT_ETH_RPC_PORT", "18545"), method, params);
    }

    JsonNode sol(String method, List<Object> params) {
        return rpc("http://localhost:" + cert("CERT_SOL_RPC_PORT", "18899"), method, params);
    }

    String randomEthAddress() {
        byte[] bytes = new byte[20];
        ThreadLocalRandom.current().nextBytes(bytes);
        return "0x" + HexFormat.of().formatHex(bytes);
    }

    String randomSolanaAddress() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return base58(bytes);
    }

    String mineBitcoinBlocks(int count) {
        String miner = btcWallet("getnewaddress", List.of("miner", "bech32")).asText();
        JsonNode blocks = btcWallet("generatetoaddress", List.of(count, miner));
        return blocks.path(blocks.size() - 1).asText();
    }

    void mineEthereumBlocks(int count) {
        eth("anvil_mine", List.of("0x" + Integer.toHexString(count)));
    }

    void waitForSolanaSignature(String signature) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            JsonNode status = sol("getSignatureStatuses", List.of(
                List.of(signature),
                Map.of("searchTransactionHistory", true)
            )).path("value").path(0);
            if (!status.isMissingNode() && !status.isNull()) {
                return;
            }
            Thread.sleep(1_000);
        }
        throw new AssertionError("Solana signature was not observed: " + signature);
    }

    private void configureNativeAssets() {
        UUID ops = activeUser("ops-" + UUID.randomUUID() + "@cert.helium.local");
        bootstrapRole(ops, Role.TREASURY_ADMIN);
        authenticateAs(ops);
        assetPort.registerAsset(new RegisterAssetCommand("BTC", "Bitcoin", 8, true, true));
        assetPort.registerNetwork(new RegisterNetworkCommand(
            "BTC", "BTC", "Bitcoin Regtest", 6, true, true,
            new BigDecimal("0.000100000000000000"),
            new BigDecimal("0.000010000000000000")
        ));
        assetPort.registerAsset(new RegisterAssetCommand("ETH", "Ethereum", 18, true, true));
        assetPort.registerNetwork(new RegisterNetworkCommand(
            "ETH", "ETH", "Ethereum Anvil", 12, true, true,
            new BigDecimal("0.001000000000000000"),
            new BigDecimal("0.000500000000000000")
        ));
        assetPort.registerAsset(new RegisterAssetCommand("SOL", "Solana", 9, true, true));
        assetPort.registerNetwork(new RegisterNetworkCommand(
            "SOL", "SOL", "Solana Test Validator", 32, true, true,
            new BigDecimal("0.010000000000000000"),
            new BigDecimal("0.000005000000000000")
        ));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            userId.toString(),
            "certification",
            AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private void bootstrapRole(UUID userId, Role role) {
        jdbcTemplate.update(
            """
            insert into auth_role_grants (id, user_id, role, granted_by, granted_at)
            values (?, ?, ?, ?, now())
            """,
            UUID.randomUUID(),
            userId,
            role.name(),
            userId
        );
    }

    private void ensureBitcoinWallet() {
        try {
            rpc(bitcoinWalletUrl(), "getwalletinfo", List.of());
        } catch (RuntimeException missingWallet) {
            try {
                btc("createwallet", List.of("helium-cert", false, false, "", false, true));
            } catch (RuntimeException createFailure) {
                String message = createFailure.getMessage() == null ? "" : createFailure.getMessage();
                if (!message.contains("Database already exists") && !message.contains("already exists")) {
                    throw createFailure;
                }
                btc("loadwallet", List.of("helium-cert"));
            }
        }
    }

    private static JsonNode rpc(String nodeUrl, String method, List<Object> params) {
        try {
            URI uri = URI.create(nodeUrl);
            HttpRequest.Builder builder = HttpRequest.newBuilder(strippedUserInfo(uri))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(uri.getUserInfo().getBytes(StandardCharsets.UTF_8)));
            }
            var body = RPC_MAPPER.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("id", "helium-certification");
            body.put("method", method);
            body.set("params", RPC_MAPPER.valueToTree(params));
            HttpResponse<String> response = HTTP.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(RPC_MAPPER.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(method + " failed with HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = RPC_MAPPER.readTree(response.body());
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException(method + " failed: " + root.path("error"));
            }
            return root.path("result");
        } catch (IOException exception) {
            throw new IllegalStateException("RPC call failed for " + method + ": " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RPC call was interrupted for " + method, exception);
        }
    }

    private static URI strippedUserInfo(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid RPC URL", exception);
        }
    }

    static String bitcoinNodeUrl() {
        return "http://" + cert("CERT_BTC_RPC_USER", "helium") + ":" + cert("CERT_BTC_RPC_PASSWORD", "helium")
            + "@localhost:" + cert("CERT_BTC_RPC_PORT", "18443");
    }

    private static String bitcoinWalletUrl() {
        return bitcoinNodeUrl() + "/wallet/helium-cert";
    }

    static String cert(String key, String fallback) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return envFileValue(key).orElse(fallback);
    }

    private static Optional<String> envFileValue(String key) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path env = current.resolve(".env");
            if (Files.isRegularFile(env)) {
                try {
                    return Files.readAllLines(env).stream()
                        .map(String::trim)
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .filter(line -> line.startsWith(key + "="))
                        .map(line -> line.substring(key.length() + 1).trim())
                        .map(AbstractBlockchainCertificationIntegrationTest::unquote)
                        .findFirst();
                } catch (IOException exception) {
                    throw new IllegalStateException("could not read " + env, exception);
                }
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static String unquote(String value) {
        String clean = value.trim();
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            return clean.substring(1, clean.length() - 1);
        }
        return clean;
    }

    private static String base58(byte[] input) {
        char[] alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
        BigInteger value = new BigInteger(1, input);
        StringBuilder encoded = new StringBuilder();
        BigInteger base = BigInteger.valueOf(58);
        while (value.signum() > 0) {
            BigInteger[] divRem = value.divideAndRemainder(base);
            encoded.append(alphabet[divRem[1].intValue()]);
            value = divRem[0];
        }
        for (byte current : input) {
            if (current == 0) {
                encoded.append(alphabet[0]);
            } else {
                break;
            }
        }
        return encoded.reverse().toString();
    }
}
