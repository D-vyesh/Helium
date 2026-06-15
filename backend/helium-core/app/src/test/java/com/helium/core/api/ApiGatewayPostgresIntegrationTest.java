package com.helium.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.app.HeliumCoreApplication;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ApiGatewayPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("helium.outbox.enabled", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @BeforeEach
    void clearData() {
        jdbcTemplate.execute("""
            truncate table
                admin_manual_reconciliation_cases,
                admin_reconciliation_discrepancies,
                admin_reconciliation_reports,
                admin_daily_balance_snapshots,
                admin_audit_events,
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
                matching_executions,
                matching_orders,
                matching_sequences,
                matching_market_state,
                trading_order_history,
                trading_settlement_instructions,
                trading_order_reservations,
                trading_fee_schedules,
                trading_orders,
                trading_markets,
                market_data_order_book_deltas,
                market_data_order_book_snapshots,
                market_data_candles,
                market_data_tickers,
                market_data_public_trades,
                market_data_sequences,
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
    }

    @Test
    void exposesAuthSessionAndProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"api-user@example.com","displayName":"API User","password":"Initial-password-123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerificationRequired").value(true));
        String token = verifyAndLogin("api-user@example.com");

        mockMvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("api-user@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"not-an-email","displayName":"","password":"short"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void protectsAdminApisAndGeneratesOpenApi() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"normal-api@example.com","displayName":"Normal User","password":"Initial-password-123"}
                    """))
            .andExpect(status().isOk());
        String userToken = verifyAndLogin("normal-api@example.com");

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        UUID adminId = registerVerifiedUser("admin-api@example.com");
        bootstrapRole(adminId, "ADMIN");
        String adminToken = login("admin-api@example.com");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").exists());

        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void exposesPublicMarketDataAndWebSocketChannel() throws Exception {
        mockMvc.perform(get("/api/v1/markets"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/markets/BTC-USD/orderbook"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.marketSymbol").value("BTC-USD"));

        ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(1);
        WebSocketSession session = new StandardWebSocketClient()
            .execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    messages.offer(message.getPayload());
                }
            }, "ws://localhost:" + port + "/ws/markets/BTC-USD/trades")
            .get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();
        assertThat(messages.poll(5, TimeUnit.SECONDS)).contains("connected");
        session.close();
    }

    @Test
    void supportsApiKeyHmacAuthenticationAndRevocation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"api-key-user@example.com","displayName":"API Key User","password":"Initial-password-123"}
                    """))
            .andExpect(status().isOk());
        String sessionToken = verifyAndLogin("api-key-user@example.com");

        String created = mockMvc.perform(post("/api/v1/api-keys")
                .header("Authorization", "Bearer " + sessionToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"label":"automation","ipAllowlist":[]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keyId").exists())
            .andExpect(jsonPath("$.secret").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(created);
        String keyId = json.get("keyId").asText();
        String presentedKey = json.get("secret").asText();
        String rawSecret = presentedKey.substring(presentedKey.indexOf('.') + 1);
        String timestamp = Instant.now().toString();
        String canonical = "GET\n/api/v1/auth/session\n\n" + timestamp + "\n";

        mockMvc.perform(get("/api/v1/auth/session")
                .header("X-API-Key", presentedKey)
                .header("X-API-Timestamp", timestamp)
                .header("X-API-Signature", hmac(rawSecret, canonical)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("api-key-user@example.com"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from api_key_audit_events where key_id = ?", Integer.class, keyId))
            .isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/api-keys/" + keyId)
                .header("Authorization", "Bearer " + sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revoked").value(true));

        mockMvc.perform(get("/api/v1/auth/session")
                .header("X-API-Key", presentedKey)
                .header("X-API-Timestamp", timestamp)
                .header("X-API-Signature", hmac(rawSecret, canonical)))
            .andExpect(status().isUnauthorized());
    }

    private String verifyAndLogin(String email) throws Exception {
        String token = jdbcTemplate.queryForObject(
            """
            select token_hash
            from auth_email_verification_tokens token
            join auth_user_accounts account on account.id = token.user_id
            where account.email = ?
            """,
            String.class,
            email
        );
        // The raw verification token is intentionally unavailable; activate directly for gateway tests.
        jdbcTemplate.update("update auth_user_accounts set status = 'ACTIVE', email_verified_at = now() where email = ?", email);
        return login(email);
    }

    private UUID registerVerifiedUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"displayName\":\"Admin API\",\"password\":\"Initial-password-123\"}"))
            .andExpect(status().isOk());
        jdbcTemplate.update("update auth_user_accounts set status = 'ACTIVE', email_verified_at = now() where email = ?", email);
        return jdbcTemplate.queryForObject("select id from auth_user_accounts where email = ?", UUID.class, email);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Initial-password-123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("sessionToken").asText();
    }

    private void bootstrapRole(UUID userId, String role) {
        jdbcTemplate.update(
            "insert into auth_role_grants (id, user_id, role, granted_by, granted_at) values (?, ?, ?, ?, now())",
            UUID.randomUUID(),
            userId,
            role,
            userId
        );
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
