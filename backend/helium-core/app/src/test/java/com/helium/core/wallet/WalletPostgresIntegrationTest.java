package com.helium.core.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.application.TrustedSystemActorAuthentication;
import com.helium.core.authuser.domain.Role;
import com.helium.core.wallet.application.AddressPort;
import com.helium.core.wallet.application.ApproveWithdrawalCommand;
import com.helium.core.wallet.application.AssetPort;
import com.helium.core.wallet.application.AssignDepositAddressCommand;
import com.helium.core.wallet.application.ConfirmWithdrawalCommand;
import com.helium.core.wallet.application.DepositPort;
import com.helium.core.wallet.application.DepositView;
import com.helium.core.wallet.application.DetectDepositCommand;
import com.helium.core.wallet.application.ObserveWithdrawalCommand;
import com.helium.core.wallet.application.RecordBroadcastCommand;
import com.helium.core.wallet.application.RegisterAssetCommand;
import com.helium.core.wallet.application.RegisterNetworkCommand;
import com.helium.core.wallet.application.RequestWithdrawalCommand;
import com.helium.core.wallet.application.ReconciliationSnapshot;
import com.helium.core.wallet.application.UpdateChainMonitorCommand;
import com.helium.core.wallet.application.UpdateDepositConfirmationsCommand;
import com.helium.core.wallet.application.WalletReconciliationPort;
import com.helium.core.wallet.application.WithdrawalApprovalPort;
import com.helium.core.wallet.application.WithdrawalRequestPort;
import com.helium.core.wallet.application.WithdrawalView;
import com.helium.core.wallet.domain.DepositStatus;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.WithdrawalStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class WalletPostgresIntegrationTest {
    private static final SecurityContextData AUTH_CONTEXT = new SecurityContextData("127.0.0.1", "wallet-test");
    private static final String PASSWORD = "Initial-password-123";
    private static final String CHAIN_MONITOR_ACTOR = TrustedSystemActorAuthentication.CHAIN_MONITOR_ACTOR_ID;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegistrationPort registrationPort;

    @Autowired
    private EmailVerificationPort emailVerificationPort;

    @Autowired
    private AssetPort assetPort;

    @Autowired
    private AddressPort addressPort;

    @Autowired
    private DepositPort depositPort;

    @Autowired
    private WithdrawalRequestPort withdrawalRequestPort;

    @Autowired
    private WithdrawalApprovalPort withdrawalApprovalPort;

    @Autowired
    private WalletReconciliationPort reconciliationPort;

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
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postsConfirmedDepositToLedgerExactlyOnce() {
        UUID opsId = operationsActor("ops-deposit@example.com");
        configureUsdtTron();
        UUID userId = activeUser("deposit@example.com");
        assignAddress(userId, "T-DEPOSIT-1");

        authenticateChainMonitor();
        DepositView detected = depositPort.detectDeposit(new DetectDepositCommand(
            "TRON",
            "T-DEPOSIT-1",
            "chain-tx-1",
            0,
            new BigDecimal("100.00")
        ));
        DepositView replay = depositPort.detectDeposit(new DetectDepositCommand(
            "TRON",
            "T-DEPOSIT-1",
            "chain-tx-1",
            0,
            new BigDecimal("100.00")
        ));
        DepositView posted = depositPort.updateConfirmations(new UpdateDepositConfirmationsCommand(detected.depositId(), 20));

        assertThat(detected.status()).isEqualTo(DepositStatus.DETECTED);
        assertThat(posted.status()).isEqualTo(DepositStatus.POSTED);
        assertThat(replay.depositId()).isEqualTo(detected.depositId());
        assertThat(countRows("wallet_chain_transaction_observations")).isEqualTo(1);
        assertThat(countRows("ledger_transactions")).isEqualTo(1);
        assertThat(balance("USER", userId.toString(), "USDT", "AVAILABLE")).isEqualByComparingTo("100.00");
        assertThat(balance("EXTERNAL", "chain:TRON:USDT", "USDT", "EXTERNAL")).isEqualByComparingTo("100.00");
        assertThat(auditActor("DEPOSIT_POSTED")).isEqualTo(CHAIN_MONITOR_ACTOR);
        assertThat(auditActor("NETWORK_REGISTERED")).isEqualTo(opsId.toString());
    }

    @Test
    void rejectsFakeDepositAttemptsFromNonSystemActors() {
        operationsActor("ops-fake-deposit@example.com");
        configureUsdtTron();
        UUID userId = activeUser("fake-deposit@example.com");
        assignAddress(userId, "T-FAKE-DEPOSIT");

        authenticateAs(userId);

        assertThatThrownBy(() -> depositPort.detectDeposit(new DetectDepositCommand(
            "TRON",
            "T-FAKE-DEPOSIT",
            "fake-chain-tx",
            0,
            new BigDecimal("50.00")
        ))).isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("trusted chain monitor");
        assertThat(countRows("wallet_deposits")).isZero();
        assertThat(countRows("ledger_transactions")).isZero();
    }

    @Test
    void rejectsFakeChainMonitorPrincipalFromNormalAuthentication() {
        operationsActor("ops-fake-chain-monitor@example.com");
        configureUsdtTron();
        UUID userId = activeUser("fake-chain-monitor@example.com");
        assignAddress(userId, "T-FAKE-CHAIN-MONITOR");

        authenticateFakeChainMonitorPrincipal();

        assertThatThrownBy(() -> depositPort.detectDeposit(new DetectDepositCommand(
            "TRON",
            "T-FAKE-CHAIN-MONITOR",
            "fake-chain-monitor-tx",
            0,
            new BigDecimal("50.00")
        ))).isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("trusted chain monitor");
        assertThat(countRows("wallet_deposits")).isZero();
    }

    @Test
    void requiresActiveAccountForDepositAddressAssignment() {
        operationsActor("ops-address@example.com");
        configureUsdtTron();
        RegistrationResult pending = registrationPort.register(new RegistrationCommand(
            "pending@example.com",
            "Pending User",
            PASSWORD,
            AUTH_CONTEXT
        ));
        authenticateAs(pending.userId());

        assertThatThrownBy(() -> addressPort.assignDepositAddress(new AssignDepositAddressCommand(
            "USDT",
            "TRON",
            "T-PENDING",
            null
        ))).isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("not active");
    }

    @Test
    void manualWithdrawalWorkflowUsesTrustedActorsAndSettlesLedgerFunds() {
        UUID opsId = operationsActor("ops-withdraw@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("withdraw@example.com", "T-DEPOSIT-2", "chain-tx-2", "100.00");

        authenticateAs(userId);
        WithdrawalView requested = withdrawalRequestPort.requestWithdrawal(withdrawalCommand(
            "withdraw-client-1",
            "T-DESTINATION",
            "10.00"
        ));

        assertThat(requested.status()).isEqualTo(WithdrawalStatus.REQUESTED);
        assertThat(requested.userId()).isEqualTo(userId);
        assertThat(balance("USER", userId.toString(), "USDT", "AVAILABLE")).isEqualByComparingTo("89.00");
        assertThat(balance("USER", userId.toString(), "USDT", "LOCKED")).isEqualByComparingTo("11.00");

        authenticateAs(opsId);
        WithdrawalView approved = withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(requested.withdrawalId()));
        WithdrawalView broadcasted = withdrawalApprovalPort.recordBroadcast(new RecordBroadcastCommand(
            approved.withdrawalId(),
            "withdraw-chain-tx-1"
        ));

        authenticateChainMonitor();
        withdrawalApprovalPort.observeWithdrawal(new ObserveWithdrawalCommand(
            broadcasted.withdrawalId(),
            "withdraw-chain-tx-1",
            new BigDecimal("10.00"),
            "T-DESTINATION",
            null,
            20
        ));
        WithdrawalView confirmed = withdrawalApprovalPort.confirm(new ConfirmWithdrawalCommand(
            broadcasted.withdrawalId()
        ));

        assertThat(confirmed.status()).isEqualTo(WithdrawalStatus.CONFIRMED);
        assertThat(balance("USER", userId.toString(), "USDT", "AVAILABLE")).isEqualByComparingTo("89.00");
        assertThat(balance("USER", userId.toString(), "USDT", "LOCKED")).isEqualByComparingTo("0.00");
        assertThat(balance("FEE", "fee:USDT", "USDT", "FEE")).isEqualByComparingTo("1.00");
        assertThat(balance("EXTERNAL", "chain:TRON:USDT", "USDT", "EXTERNAL")).isEqualByComparingTo("90.00");
        assertThat(auditActor("WITHDRAWAL_APPROVED")).isEqualTo(opsId.toString());
        assertThat(auditActor("WITHDRAWAL_CONFIRMED")).isEqualTo(CHAIN_MONITOR_ACTOR);
    }

    @Test
    void rejectsUnauthorizedWithdrawalApprovalBeforeMutation() {
        operationsActor("ops-unauthorized-approval@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("unauthorized-approval@example.com", "T-DEPOSIT-3", "chain-tx-3", "100.00");
        WithdrawalView requested = requestWithdrawalAs(userId, "withdraw-client-unauthorized", "T-DESTINATION", "10.00");

        authenticateAs(userId);

        assertThatThrownBy(() -> withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(requested.withdrawalId())))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("not authorized");
        assertThat(withdrawalStatus(requested.withdrawalId())).isEqualTo("REQUESTED");
    }

    @Test
    void rejectsUnauthorizedWithdrawalConfirmationBeforeMutation() {
        UUID opsId = operationsActor("ops-unauthorized-confirm@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("unauthorized-confirm@example.com", "T-DEPOSIT-4", "chain-tx-4", "100.00");
        WithdrawalView broadcasted = approvedAndBroadcastedWithdrawal(userId, opsId, "withdraw-client-confirm-unauthorized");

        authenticateAs(opsId);

        assertThatThrownBy(() -> withdrawalApprovalPort.confirm(new ConfirmWithdrawalCommand(
            broadcasted.withdrawalId()
        ))).isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("trusted chain monitor");
        assertThat(withdrawalStatus(broadcasted.withdrawalId())).isEqualTo("BROADCASTED");
        assertThat(countRows("wallet_chain_transaction_observations", "direction = 'WITHDRAWAL'")).isZero();
    }

    @Test
    void doesNotAllowWithdrawalActorSpoofingThroughCommands() {
        UUID opsId = operationsActor("ops-spoof@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("spoof@example.com", "T-DEPOSIT-5", "chain-tx-5", "100.00");
        WithdrawalView requested = requestWithdrawalAs(userId, "withdraw-client-spoof", "T-DESTINATION", "10.00");

        authenticateAs(opsId);
        withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(requested.withdrawalId()));

        assertThat(auditActor("WITHDRAWAL_APPROVED")).isEqualTo(opsId.toString());
        assertThat(jdbcTemplate.queryForObject(
            "select approved_by from wallet_withdrawals where id = ?",
            String.class,
            requested.withdrawalId()
        )).isEqualTo(opsId.toString());
    }

    @Test
    void withdrawalRequestAlwaysUsesAuthenticatedUserAsOwner() {
        operationsActor("ops-owner@example.com");
        configureUsdtTron();
        UUID firstUser = fundedUser("owner-a@example.com", "T-DEPOSIT-6A", "chain-tx-6a", "100.00");
        UUID secondUser = fundedUser("owner-b@example.com", "T-DEPOSIT-6B", "chain-tx-6b", "100.00");

        authenticateAs(secondUser);
        WithdrawalView requested = withdrawalRequestPort.requestWithdrawal(withdrawalCommand(
            "withdraw-client-owner",
            "T-DESTINATION",
            "10.00"
        ));

        assertThat(requested.userId()).isEqualTo(secondUser);
        assertThat(requested.userId()).isNotEqualTo(firstUser);
        assertThat(jdbcTemplate.queryForObject(
            "select user_id from wallet_withdrawals where client_request_id = 'withdraw-client-owner'",
            UUID.class
        )).isEqualTo(secondUser);
    }

    @Test
    void validatesWithdrawalReplayPayloadHash() {
        operationsActor("ops-replay@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("replay@example.com", "T-DEPOSIT-7", "chain-tx-7", "100.00");

        authenticateAs(userId);
        WithdrawalView first = withdrawalRequestPort.requestWithdrawal(withdrawalCommand(
            "withdraw-client-replay",
            "T-DESTINATION",
            "10.00"
        ));
        WithdrawalView replay = withdrawalRequestPort.requestWithdrawal(withdrawalCommand(
            "withdraw-client-replay",
            "T-DESTINATION",
            "10.00"
        ));

        assertThat(replay.withdrawalId()).isEqualTo(first.withdrawalId());
        assertThatThrownBy(() -> withdrawalRequestPort.requestWithdrawal(withdrawalCommand(
            "withdraw-client-replay",
            "T-OTHER-DESTINATION",
            "10.00"
        ))).isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("payload differs");
        assertThat(countRows("wallet_withdrawals", "client_request_id = 'withdraw-client-replay'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:withdrawal-reserve:%:withdraw-client-replay'")).isEqualTo(1);
    }

    @Test
    void scopesWithdrawalIdempotencyPerUser() {
        operationsActor("ops-user-scoped-idempotency@example.com");
        configureUsdtTron();
        UUID firstUser = fundedUser("scoped-a@example.com", "T-DEPOSIT-SCOPE-A", "chain-tx-scope-a", "100.00");
        UUID secondUser = fundedUser("scoped-b@example.com", "T-DEPOSIT-SCOPE-B", "chain-tx-scope-b", "100.00");

        WithdrawalView first = requestWithdrawalAs(firstUser, "same-client-request-id", "T-DESTINATION-A", "10.00");
        WithdrawalView second = requestWithdrawalAs(secondUser, "same-client-request-id", "T-DESTINATION-B", "10.00");

        assertThat(first.withdrawalId()).isNotEqualTo(second.withdrawalId());
        assertThat(first.userId()).isEqualTo(firstUser);
        assertThat(second.userId()).isEqualTo(secondUser);
        assertThat(countRows("wallet_withdrawals", "client_request_id = 'same-client-request-id'")).isEqualTo(2);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:withdrawal-reserve:%:same-client-request-id'")).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateWithdrawalRequestsReplaySafely() throws Exception {
        operationsActor("ops-concurrent-withdrawal@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("concurrent-withdrawal@example.com", "T-DEPOSIT-8", "chain-tx-8", "100.00");

        List<WithdrawalView> results = runConcurrently(6, () -> {
            authenticateAs(userId);
            return withdrawalRequestPort.requestWithdrawal(withdrawalCommand(
                "withdraw-client-concurrent",
                "T-DESTINATION",
                "10.00"
            ));
        });

        assertThat(results).extracting(WithdrawalView::withdrawalId).containsOnly(results.getFirst().withdrawalId());
        assertThat(countRows("wallet_withdrawals", "client_request_id = 'withdraw-client-concurrent'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference like 'wallet:withdrawal-reserve:%:withdraw-client-concurrent'")).isEqualTo(1);
        assertThat(balance("USER", userId.toString(), "USDT", "AVAILABLE")).isEqualByComparingTo("89.00");
        assertThat(balance("USER", userId.toString(), "USDT", "LOCKED")).isEqualByComparingTo("11.00");
    }

    @Test
    void concurrentDuplicateDepositProcessingReplaysSafely() throws Exception {
        operationsActor("ops-concurrent-deposit@example.com");
        configureUsdtTron();
        UUID userId = activeUser("concurrent-deposit@example.com");
        assignAddress(userId, "T-DEPOSIT-9");

        List<DepositView> results = runConcurrently(6, () -> {
            authenticateChainMonitor();
            return depositPort.detectDeposit(new DetectDepositCommand(
                "TRON",
                "T-DEPOSIT-9",
                "chain-tx-9",
                0,
                new BigDecimal("100.00")
            ));
        });

        assertThat(results).extracting(DepositView::depositId).containsOnly(results.getFirst().depositId());
        assertThat(countRows("wallet_deposits", "tx_hash = 'chain-tx-9'")).isEqualTo(1);
        assertThat(countRows("wallet_chain_transaction_observations", "tx_hash = 'chain-tx-9'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions")).isZero();
    }

    @Test
    void blocksApprovalWhenNetworkIsDisabled() {
        UUID opsId = operationsActor("ops-disabled-network@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("disabled-network@example.com", "T-DEPOSIT-10", "chain-tx-10", "100.00");
        WithdrawalView requested = requestWithdrawalAs(userId, "withdraw-client-disabled-network", "T-DESTINATION", "10.00");
        jdbcTemplate.update("update wallet_blockchain_networks set withdrawal_enabled = false where network_code = 'TRON'");

        authenticateAs(opsId);

        assertThatThrownBy(() -> withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(requested.withdrawalId())))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("withdrawals are disabled");
        assertThat(withdrawalStatus(requested.withdrawalId())).isEqualTo("REQUESTED");
    }

    @Test
    void blocksApprovalWhenWithdrawalOwnerIsSuspended() {
        UUID opsId = operationsActor("ops-suspended-user@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("suspended-user@example.com", "T-DEPOSIT-11", "chain-tx-11", "100.00");
        WithdrawalView requested = requestWithdrawalAs(userId, "withdraw-client-suspended-user", "T-DESTINATION", "10.00");
        jdbcTemplate.update("update auth_user_accounts set status = 'SUSPENDED' where id = ?", userId);

        authenticateAs(opsId);

        assertThatThrownBy(() -> withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(requested.withdrawalId())))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("not active");
        assertThat(withdrawalStatus(requested.withdrawalId())).isEqualTo("REQUESTED");
    }

    @Test
    void withdrawalConfirmationFailsWhenNoPriorObservationExists() {
        UUID opsId = operationsActor("ops-no-observation@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("no-observation@example.com", "T-DEPOSIT-NO-OBS", "chain-tx-no-obs", "100.00");
        WithdrawalView broadcasted = approvedAndBroadcastedWithdrawal(userId, opsId, "withdraw-client-no-observation");

        authenticateChainMonitor();

        assertThatThrownBy(() -> withdrawalApprovalPort.confirm(new ConfirmWithdrawalCommand(broadcasted.withdrawalId())))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("chain observation was not found");
        assertThat(withdrawalStatus(broadcasted.withdrawalId())).isEqualTo("BROADCASTED");
        assertThat(countRows("ledger_transactions", "business_reference = 'wallet:withdrawal-settle:" + broadcasted.withdrawalId() + "'")).isZero();
    }

    @Test
    void rejectsChainObservationMismatchBeforeSettlement() {
        UUID opsId = operationsActor("ops-chain-mismatch@example.com");
        configureUsdtTron();
        UUID userId = fundedUser("chain-mismatch@example.com", "T-DEPOSIT-12", "chain-tx-12", "100.00");
        WithdrawalView broadcasted = approvedAndBroadcastedWithdrawal(userId, opsId, "withdraw-client-chain-mismatch");

        authenticateChainMonitor();

        assertThatThrownBy(() -> withdrawalApprovalPort.observeWithdrawal(new ObserveWithdrawalCommand(
            broadcasted.withdrawalId(),
            "withdraw-chain-tx-withdraw-client-chain-mismatch",
            new BigDecimal("9.00"),
            "T-DESTINATION",
            null,
            20
        ))).isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("chain observation does not match withdrawal");
        assertThat(withdrawalStatus(broadcasted.withdrawalId())).isEqualTo("BROADCASTED");
        assertThat(countRows("ledger_transactions", "business_reference = 'wallet:withdrawal-settle:" + broadcasted.withdrawalId() + "'")).isZero();
    }

    @Test
    void updatesChainMonitorAndBuildsAmountBasedReconciliationSnapshot() {
        UUID opsId = operationsActor("ops-reconcile@example.com");
        configureUsdtTron();
        fundedUser("reconcile@example.com", "T-DEPOSIT-13", "chain-tx-13", "25.00");

        authenticateChainMonitor();
        reconciliationPort.updateChainMonitor(new UpdateChainMonitorCommand("TRON", 12345));
        authenticateAs(opsId);
        ReconciliationSnapshot snapshot = reconciliationPort.snapshot("USDT", "TRON");

        assertThat(snapshot.walletTotal()).isEqualByComparingTo("25.00");
        assertThat(snapshot.ledgerTotal()).isEqualByComparingTo("25.00");
        assertThat(snapshot.chainTotal()).isEqualByComparingTo("25.00");
        assertThat(snapshot.discrepancyCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select last_observed_block_height from wallet_chain_monitor_states where network_code = 'TRON'",
            Long.class
        )).isEqualTo(12345L);
    }

    @Test
    void recordsReconciliationDiscrepanciesWithoutAdjustingBalances() {
        UUID opsId = operationsActor("ops-reconcile-discrepancy@example.com");
        configureUsdtTron();
        fundedUser("reconcile-discrepancy@example.com", "T-DEPOSIT-14", "chain-tx-14", "25.00");
        jdbcTemplate.update("update wallet_chain_transaction_observations set amount = 24.00 where tx_hash = 'chain-tx-14'");

        authenticateAs(opsId);
        ReconciliationSnapshot snapshot = reconciliationPort.snapshot("USDT", "TRON");

        assertThat(snapshot.walletTotal()).isEqualByComparingTo("25.00");
        assertThat(snapshot.ledgerTotal()).isEqualByComparingTo("25.00");
        assertThat(snapshot.chainTotal()).isEqualByComparingTo("24.00");
        assertThat(snapshot.discrepancyCount()).isEqualTo(1);
        assertThat(countRows("wallet_reconciliation_discrepancies")).isEqualTo(1);
    }

    private UUID operationsActor(String email) {
        UUID actorId = activeUser(email);
        bootstrapRole(actorId, Role.TREASURY_ADMIN);
        authenticateAs(actorId);
        return actorId;
    }

    private UUID activeUser(String email) {
        SecurityContextHolder.clearContext();
        RegistrationResult result = registrationPort.register(new RegistrationCommand(email, "Wallet User", PASSWORD, AUTH_CONTEXT));
        emailVerificationPort.verify(result.emailVerificationToken(), AUTH_CONTEXT);
        return result.userId();
    }

    private void configureUsdtTron() {
        assetPort.registerAsset(new RegisterAssetCommand("USDT", "Tether USD", 6, true, true));
        assetPort.registerNetwork(new RegisterNetworkCommand(
            "TRON",
            "USDT",
            "Tron",
            20,
            true,
            true,
            new BigDecimal("1.00"),
            new BigDecimal("1.00")
        ));
    }

    private UUID fundedUser(String email, String address, String txHash, String amount) {
        UUID userId = activeUser(email);
        assignAddress(userId, address);
        authenticateChainMonitor();
        DepositView detected = depositPort.detectDeposit(new DetectDepositCommand(
            "TRON",
            address,
            txHash,
            0,
            new BigDecimal(amount)
        ));
        DepositView posted = depositPort.updateConfirmations(new UpdateDepositConfirmationsCommand(detected.depositId(), 20));
        assertThat(posted.status()).isEqualTo(DepositStatus.POSTED);
        return userId;
    }

    private void assignAddress(UUID userId, String address) {
        authenticateAs(userId);
        addressPort.assignDepositAddress(new AssignDepositAddressCommand("USDT", "TRON", address, null));
    }

    private WithdrawalView requestWithdrawalAs(UUID userId, String clientRequestId, String destination, String amount) {
        authenticateAs(userId);
        return withdrawalRequestPort.requestWithdrawal(withdrawalCommand(clientRequestId, destination, amount));
    }

    private WithdrawalView approvedAndBroadcastedWithdrawal(UUID userId, UUID opsId, String clientRequestId) {
        WithdrawalView requested = requestWithdrawalAs(userId, clientRequestId, "T-DESTINATION", "10.00");
        authenticateAs(opsId);
        WithdrawalView approved = withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(requested.withdrawalId()));
        return withdrawalApprovalPort.recordBroadcast(new RecordBroadcastCommand(
            approved.withdrawalId(),
            "withdraw-chain-tx-" + clientRequestId
        ));
    }

    private RequestWithdrawalCommand withdrawalCommand(String clientRequestId, String destination, String amount) {
        return new RequestWithdrawalCommand(
            clientRequestId,
            "USDT",
            "TRON",
            destination,
            null,
            new BigDecimal(amount)
        );
    }

    private <T> List<T> runConcurrently(int workers, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = java.util.stream.IntStream.range(0, workers)
                .mapToObj(ignored -> executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }))
                .toList();
            ready.await();
            start.countDown();
            return futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            userId.toString(),
            "test",
            AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private void authenticateChainMonitor() {
        SecurityContextHolder.getContext().setAuthentication(TrustedSystemActorAuthentication.chainMonitor());
    }

    private void authenticateFakeChainMonitorPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            CHAIN_MONITOR_ACTOR,
            "test",
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

    private String auditActor(String eventType) {
        return jdbcTemplate.queryForObject(
            "select actor_id from wallet_audit_events where event_type = ? order by occurred_at desc limit 1",
            String.class,
            eventType
        );
    }

    private String withdrawalStatus(UUID withdrawalId) {
        return jdbcTemplate.queryForObject("select status from wallet_withdrawals where id = ?", String.class, withdrawalId);
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    private long countRows(String tableName, String whereClause) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Long.class);
        return count == null ? 0 : count;
    }

    private BigDecimal balance(String ownerType, String ownerId, String assetCode, String balanceType) {
        return jdbcTemplate.queryForObject(
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
    }
}
