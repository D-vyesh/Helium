package com.helium.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.ledger.application.CreateLedgerAccountCommand;
import com.helium.core.ledger.application.LedgerAccountPort;
import com.helium.core.ledger.application.LedgerAccountView;
import com.helium.core.ledger.application.LedgerPostingCommand;
import com.helium.core.ledger.application.LedgerPostingPort;
import com.helium.core.ledger.application.LedgerPostingResult;
import com.helium.core.ledger.application.PostingLineCommand;
import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.LedgerValidationException;
import com.helium.core.ledger.domain.PostingDirection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class)
@Testcontainers
class LedgerPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LedgerAccountPort ledgerAccountPort;

    @Autowired
    private LedgerPostingPort ledgerPostingPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearLedger() {
        jdbcTemplate.execute("""
            truncate table
                ledger_idempotency_records,
                ledger_posting_lines,
                ledger_transactions,
                ledger_balance_snapshots,
                ledger_accounts
            cascade
            """);
    }

    @Test
    void serializesConcurrentIdempotentPostings() throws Exception {
        LedgerAccountView external = ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.EXTERNAL,
            "chain:btc",
            "BTC",
            BalanceType.EXTERNAL
        ));
        LedgerAccountView user = ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            "user-1",
            "BTC",
            BalanceType.AVAILABLE
        ));
        LedgerPostingCommand command = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            AuditMetadata.of("system", "ledger-test", "corr-1", "cause-1", "test"),
            List.of(
                new PostingLineCommand(external.accountId(), PostingDirection.DEBIT, BigDecimal.ONE),
                new PostingLineCommand(user.accountId(), PostingDirection.CREDIT, BigDecimal.ONE)
            )
        );

        List<LedgerPostingResult> results = runConcurrently(() -> ledgerPostingPort.post(command));

        assertThat(results).extracting(LedgerPostingResult::transactionId).containsOnly(results.getFirst().transactionId());
        assertThat(results).extracting(LedgerPostingResult::idempotentReplay).containsExactlyInAnyOrder(false, true);
        assertThat(countRows("ledger_transactions")).isEqualTo(1);
        assertThat(countRows("ledger_posting_lines")).isEqualTo(2);
        assertThat(countRows("ledger_idempotency_records")).isEqualTo(1);
    }

    @Test
    void createsOneAccountAndSnapshotDuringConcurrentAccountOpening() throws Exception {
        CreateLedgerAccountCommand command = new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            "user-1",
            "USDT",
            BalanceType.AVAILABLE
        );

        List<LedgerAccountView> results = runConcurrently(() -> ledgerAccountPort.openAccount(command));

        assertThat(results).extracting(LedgerAccountView::accountId).containsOnly(results.getFirst().accountId());
        assertThat(countRows("ledger_accounts")).isEqualTo(1);
        assertThat(countRows("ledger_balance_snapshots")).isEqualTo(1);
    }

    @Test
    void databaseRejectsUnbalancedTransactionAtCommit() throws SQLException {
        UUID debitAccount = insertAccount(false);
        UUID creditAccount = insertAccount(false);
        UUID transactionId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertTransaction(connection, transactionId, "idem-unbalanced");
            insertPostingLine(connection, transactionId, debitAccount, PostingDirection.DEBIT, new BigDecimal("1.00"));
            insertPostingLine(connection, transactionId, creditAccount, PostingDirection.CREDIT, new BigDecimal("0.99"));

            assertThatThrownBy(connection::commit)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("not balanced per asset");
        }
    }

    @Test
    void databaseRejectsMutationOfImmutableLedgerRows() throws SQLException {
        ImmutableFixture fixture = insertBalancedTransaction();

        assertThatThrownBy(() -> jdbcTemplate.update(
            "update ledger_transactions set description = ? where id = ?",
            "changed",
            fixture.transactionId()
        )).rootCause().hasMessageContaining("ledger table ledger_transactions is immutable after insert");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "update ledger_posting_lines set amount = ? where id = ?",
            new BigDecimal("2.00"),
            fixture.postingLineId()
        )).rootCause().hasMessageContaining("ledger table ledger_posting_lines is immutable after insert");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "delete from ledger_idempotency_records where idempotency_key = ?",
            fixture.idempotencyKey()
        )).rootCause().hasMessageContaining("ledger table ledger_idempotency_records is immutable after insert");
    }

    @Test
    void databaseEnforcesNegativeBalancePolicyPerAccount() {
        UUID restrictedAccount = insertAccount(false);
        UUID allowedAccount = insertAccount(true);
        insertSnapshot(restrictedAccount);
        insertSnapshot(allowedAccount);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "update ledger_balance_snapshots set current_balance = -1 where account_id = ?",
            restrictedAccount
        )).rootCause().hasMessageContaining("negative balance is not allowed for account " + restrictedAccount);

        assertThat(jdbcTemplate.update(
            "update ledger_balance_snapshots set current_balance = -1 where account_id = ?",
            allowedAccount
        )).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "update ledger_accounts set negative_balance_allowed = false where id = ?",
            allowedAccount
        )).rootCause().hasMessageContaining("negative balance policy is immutable after insert");
    }

    @Test
    void rejectsReopeningAccountWithConflictingNegativeBalancePolicy() {
        CreateLedgerAccountCommand restricted = new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            "user-policy",
            "USDT",
            BalanceType.AVAILABLE,
            false
        );
        CreateLedgerAccountCommand unrestricted = new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            "user-policy",
            "USDT",
            BalanceType.AVAILABLE,
            true
        );
        ledgerAccountPort.openAccount(restricted);

        assertThatThrownBy(() -> ledgerAccountPort.openAccount(unrestricted))
            .isInstanceOf(LedgerValidationException.class)
            .hasMessageContaining("negative balance policy");
    }

    private ImmutableFixture insertBalancedTransaction() throws SQLException {
        UUID debitAccount = insertAccount(false);
        UUID creditAccount = insertAccount(false);
        UUID transactionId = UUID.randomUUID();
        UUID postingLineId = UUID.randomUUID();
        String idempotencyKey = "idem-immutable";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            insertTransaction(connection, transactionId, idempotencyKey);
            insertPostingLine(connection, postingLineId, transactionId, debitAccount, PostingDirection.DEBIT, BigDecimal.ONE);
            insertPostingLine(connection, transactionId, creditAccount, PostingDirection.CREDIT, BigDecimal.ONE);
            try (PreparedStatement statement = connection.prepareStatement("""
                insert into ledger_idempotency_records (
                    idempotency_key,
                    transaction_id,
                    request_hash,
                    status,
                    created_at
                ) values (?, ?, ?, 'POSTED', now())
                """)) {
                statement.setString(1, idempotencyKey);
                statement.setObject(2, transactionId);
                statement.setString(3, "0".repeat(64));
                statement.executeUpdate();
            }
            connection.commit();
        }

        return new ImmutableFixture(transactionId, postingLineId, idempotencyKey);
    }

    private UUID insertAccount(boolean negativeBalanceAllowed) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update("""
            insert into ledger_accounts (
                id,
                owner_type,
                owner_id,
                asset_code,
                balance_type,
                status,
                negative_balance_allowed,
                created_at
            ) values (?, 'USER', ?, 'USDT', 'AVAILABLE', 'ACTIVE', ?, now())
            """,
            accountId,
            "user-" + accountId,
            negativeBalanceAllowed
        );
        return accountId;
    }

    private void insertSnapshot(UUID accountId) {
        jdbcTemplate.update("""
            insert into ledger_balance_snapshots (
                id,
                account_id,
                asset_code,
                current_balance,
                version,
                updated_at
            ) values (?, ?, 'USDT', 0, 0, now())
            """,
            UUID.randomUUID(),
            accountId
        );
    }

    private static void insertTransaction(Connection connection, UUID transactionId, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into ledger_transactions (
                id,
                transaction_type,
                business_reference,
                idempotency_key,
                description,
                actor_id,
                source_module,
                correlation_id,
                causation_id,
                audit_reason,
                created_at
            ) values (?, 'GENERAL', ?, ?, 'integration test', 'system', 'ledger-test', 'corr-1', 'cause-1', 'test', now())
            """)) {
            statement.setObject(1, transactionId);
            statement.setString(2, "business-" + transactionId);
            statement.setString(3, idempotencyKey);
            statement.executeUpdate();
        }
    }

    private static void insertPostingLine(
        Connection connection,
        UUID transactionId,
        UUID accountId,
        PostingDirection direction,
        BigDecimal amount
    ) throws SQLException {
        insertPostingLine(connection, UUID.randomUUID(), transactionId, accountId, direction, amount);
    }

    private static void insertPostingLine(
        Connection connection,
        UUID postingLineId,
        UUID transactionId,
        UUID accountId,
        PostingDirection direction,
        BigDecimal amount
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into ledger_posting_lines (
                id,
                transaction_id,
                account_id,
                asset_code,
                direction,
                amount
            ) values (?, ?, ?, 'USDT', ?, ?)
            """)) {
            statement.setObject(1, postingLineId);
            statement.setObject(2, transactionId);
            statement.setObject(3, accountId);
            statement.setString(4, direction.name());
            statement.setBigDecimal(5, amount);
            statement.executeUpdate();
        }
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    private static <T> List<T> runConcurrently(Callable<T> operation) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<T> synchronizedOperation = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent operation did not start");
                }
                return operation.call();
            };

            Future<T> first = executor.submit(synchronizedOperation);
            Future<T> second = executor.submit(synchronizedOperation);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private record ImmutableFixture(UUID transactionId, UUID postingLineId, String idempotencyKey) {
    }
}
