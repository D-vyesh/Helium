package com.helium.core.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.admin.application.ReconciliationPort;
import com.helium.core.admin.application.ReconciliationReportView;
import com.helium.core.admin.domain.AdminValidationException;
import com.helium.core.admin.domain.ReconciliationReportStatus;
import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.domain.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class)
@Testcontainers
class AdminReconciliationPostgresIntegrationTest {
    private static final String PASSWORD = "Initial-password-123";
    private static final SecurityContextData CONTEXT = new SecurityContextData("127.0.0.1", "admin-test");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegistrationPort registrationPort;

    @Autowired
    private EmailVerificationPort emailVerificationPort;

    @Autowired
    private ReconciliationPort reconciliationPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearData() {
        SecurityContextHolder.clearContext();
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsCleanLedgerWalletReconciliationReport() {
        UUID admin = activeOperator("admin-clean@example.com", Role.FINANCE_OPS);
        authenticateAs(admin);
        seedWalletScope("USDT", "TRON");

        ReconciliationReportView report = reconciliationPort.reconcileLedgerVsWallet("USDT", "TRON", LocalDate.of(2026, 6, 14));

        assertThat(report.status()).isEqualTo(ReconciliationReportStatus.CLEAN);
        assertThat(report.difference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(countRows("admin_reconciliation_reports")).isOne();
        assertThat(countRows("admin_reconciliation_discrepancies")).isZero();
        assertThat(lastAdminAuditActor()).isEqualTo(admin.toString());
    }

    @Test
    void detectsLedgerWalletDiscrepancyWithoutAdjustingBalances() {
        UUID admin = activeOperator("admin-discrepancy@example.com", Role.COMPLIANCE);
        authenticateAs(admin);
        seedWalletScope("BTC", "BITCOIN");
        seedLedgerExternalBalance("BTC", "BITCOIN", new BigDecimal("2.50000000"));

        ReconciliationReportView report = reconciliationPort.reconcileLedgerVsWallet("BTC", "BITCOIN", LocalDate.of(2026, 6, 14));

        assertThat(report.status()).isEqualTo(ReconciliationReportStatus.DISCREPANCY);
        assertThat(report.difference()).isEqualByComparingTo("2.50000000");
        assertThat(countRows("admin_reconciliation_discrepancies")).isOne();
        assertThat(externalBalance("BTC", "BITCOIN")).isEqualByComparingTo("2.50000000");
    }

    @Test
    void rejectsReconciliationWithoutPrivilegedRole() {
        UUID user = activeUser("admin-denied@example.com");
        authenticateAs(user);
        seedWalletScope("ETH", "ETHEREUM");

        assertThatThrownBy(() -> reconciliationPort.reconcileWalletVsChain("ETH", "ETHEREUM", LocalDate.of(2026, 6, 14)))
            .isInstanceOf(AdminValidationException.class)
            .hasMessageContaining("not authorized");
    }

    @Test
    void reconciliationRecordsAndAuditEventsAreAppendOnly() {
        UUID admin = activeOperator("admin-immutable@example.com", Role.ADMIN);
        authenticateAs(admin);
        seedWalletScope("USD", "ACH");
        seedLedgerExternalBalance("USD", "ACH", BigDecimal.ONE);

        reconciliationPort.reconcileLedgerVsWallet("USD", "ACH", LocalDate.of(2026, 6, 14));
        UUID reportId = jdbcTemplate.queryForObject("select id from admin_reconciliation_reports limit 1", UUID.class);
        UUID discrepancyId = jdbcTemplate.queryForObject("select id from admin_reconciliation_discrepancies limit 1", UUID.class);
        UUID auditId = jdbcTemplate.queryForObject("select id from admin_audit_events limit 1", UUID.class);

        assertThatThrownBy(() -> jdbcTemplate.update("update admin_reconciliation_reports set scope_key = 'changed' where id = ?", reportId))
            .rootCause()
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from admin_reconciliation_discrepancies where id = ?", discrepancyId))
            .rootCause()
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("update admin_audit_events set details = 'changed' where id = ?", auditId))
            .rootCause()
            .hasMessageContaining("append-only");
    }

    private RegistrationResult registerAndVerify(String email) {
        RegistrationResult result = registrationPort.register(new RegistrationCommand(email, "Admin Test", PASSWORD, CONTEXT));
        emailVerificationPort.verify(result.emailVerificationToken(), CONTEXT);
        return result;
    }

    private UUID activeUser(String email) {
        return registerAndVerify(email).userId();
    }

    private UUID activeOperator(String email, Role role) {
        UUID userId = activeUser(email);
        jdbcTemplate.update(
            "insert into auth_role_grants (id, user_id, role, granted_by, granted_at) values (?, ?, ?, ?, now())",
            UUID.randomUUID(),
            userId,
            role.name(),
            userId
        );
        return userId;
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            userId.toString(),
            "test",
            AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private void seedWalletScope(String assetCode, String networkCode) {
        jdbcTemplate.update(
            """
            insert into wallet_assets (code, name, scale, deposit_enabled, withdrawal_enabled, created_at, updated_at)
            values (?, ?, 18, true, true, now(), now())
            """,
            assetCode,
            assetCode
        );
        jdbcTemplate.update(
            """
            insert into wallet_blockchain_networks (
                network_code,
                asset_code,
                display_name,
                required_confirmations,
                deposit_enabled,
                withdrawal_enabled,
                minimum_withdrawal,
                withdrawal_fee,
                created_at,
                updated_at
            )
            values (?, ?, ?, 1, true, true, 0, 0, now(), now())
            """,
            networkCode,
            assetCode,
            networkCode
        );
    }

    private void seedLedgerExternalBalance(String assetCode, String networkCode, BigDecimal amount) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into ledger_accounts (
                id,
                owner_type,
                owner_id,
                asset_code,
                balance_type,
                status,
                negative_balance_allowed,
                created_at
            )
            values (?, 'EXTERNAL', ?, ?, 'EXTERNAL', 'ACTIVE', false, now())
            """,
            accountId,
            "chain:" + networkCode + ":" + assetCode,
            assetCode
        );
        jdbcTemplate.update(
            """
            insert into ledger_balance_snapshots (id, account_id, asset_code, current_balance, version, updated_at)
            values (?, ?, ?, ?, 0, now())
            """,
            UUID.randomUUID(),
            accountId,
            assetCode,
            amount
        );
    }

    private BigDecimal externalBalance(String assetCode, String networkCode) {
        return jdbcTemplate.queryForObject(
            """
            select current_balance
            from ledger_balance_snapshots snapshot
            join ledger_accounts account on account.id = snapshot.account_id
            where account.owner_id = ? and account.asset_code = ?
            """,
            BigDecimal.class,
            "chain:" + networkCode + ":" + assetCode,
            assetCode
        );
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    private String lastAdminAuditActor() {
        return jdbcTemplate.queryForObject(
            "select actor_id from admin_audit_events order by occurred_at desc limit 1",
            String.class
        );
    }
}
