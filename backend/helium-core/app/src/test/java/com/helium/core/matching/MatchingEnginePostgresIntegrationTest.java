package com.helium.core.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.ledger.application.CreateLedgerAccountCommand;
import com.helium.core.ledger.application.LedgerAccountPort;
import com.helium.core.ledger.application.LedgerAccountView;
import com.helium.core.ledger.application.LedgerPostingCommand;
import com.helium.core.ledger.application.LedgerPostingPort;
import com.helium.core.ledger.application.PostingLineCommand;
import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.PostingDirection;
import com.helium.core.matching.application.MatchingEventPort;
import com.helium.core.matching.application.OrderBookQueryPort;
import com.helium.core.trading.application.OrderCancellationPort;
import com.helium.core.trading.application.OrderPlacementPort;
import com.helium.core.trading.domain.FeeAssetType;
import com.helium.core.trading.domain.FeeSchedule;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TimeInForce;
import com.helium.core.trading.infrastructure.FeeScheduleRepository;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class)
@ActiveProfiles("matching-engine")
@Testcontainers
class MatchingEnginePostgresIntegrationTest {
    private static final SecurityContextData CONTEXT = new SecurityContextData("127.0.0.1", "matching-test");
    private static final String PASSWORD = "Initial-password-123";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegistrationPort registrationPort;

    @Autowired
    private EmailVerificationPort emailVerificationPort;

    @Autowired
    private OrderPlacementPort orderPlacementPort;

    @Autowired
    private OrderCancellationPort orderCancellationPort;

    @Autowired
    private OrderBookQueryPort orderBookQueryPort;

    @Autowired
    private List<MatchingEventPort> matchingEventPorts;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private FeeScheduleRepository feeScheduleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LedgerAccountPort ledgerAccountPort;

    @Autowired
    private LedgerPostingPort ledgerPostingPort;

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
                market_data_order_book_deltas,
                market_data_order_book_snapshots,
                market_data_candles,
                market_data_tickers,
                market_data_public_trades,
                market_data_sequences,
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
    void matchesAndSettlesThroughTradingAndLedger() {
        UUID buyerId = activeUser("matching-buyer@example.com");
        UUID sellerId = activeUser("matching-seller@example.com");
        configureMarket();
        fund(buyerId, "USD", "1000.00");
        fund(sellerId, "BTC", "2.00");

        authenticateAs(buyerId);
        UUID buyerOrderId = orderPlacementPort.placeOrder(order("buy-1", OrderSide.BUY, "1.0", "100.00"));
        assertThat(orderRepository.findById(buyerOrderId).orElseThrow().status()).isEqualTo(OrderStatus.OPEN);

        authenticateAs(sellerId);
        UUID sellerOrderId = orderPlacementPort.placeOrder(order("sell-1", OrderSide.SELL, "1.0", "99.00"));

        assertThat(orderRepository.findById(buyerOrderId).orElseThrow().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(orderRepository.findById(sellerOrderId).orElseThrow().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(balance("USER", buyerId.toString(), "USD", "AVAILABLE")).isEqualByComparingTo("900.00");
        assertThat(balance("USER", buyerId.toString(), "BTC", "AVAILABLE")).isEqualByComparingTo("1.00");
        assertThat(balance("USER", sellerId.toString(), "USD", "AVAILABLE")).isEqualByComparingTo("100.00");
        assertThat(balance("USER", sellerId.toString(), "BTC", "LOCKED")).isEqualByComparingTo("0.00");
        assertThat(countRows("matching_executions", "market_symbol = 'BTC-USD'")).isEqualTo(1);
        assertThat(countRows("trading_settlement_instructions", "execution_id = 'execution:BTC-USD:3'")).isEqualTo(1);
        assertThat(orderBookQueryPort.getOrderBook("BTC-USD").bids()).isEmpty();
        assertThat(orderBookQueryPort.getOrderBook("BTC-USD").asks()).isEmpty();
    }

    @Test
    void replayingPersistedExecutionEventIsSafe() {
        UUID buyerId = activeUser("matching-replay-buyer@example.com");
        UUID sellerId = activeUser("matching-replay-seller@example.com");
        configureMarket();
        fund(buyerId, "USD", "1000.00");
        fund(sellerId, "BTC", "2.00");

        authenticateAs(buyerId);
        UUID buyerOrderId = orderPlacementPort.placeOrder(order("buy-replay", OrderSide.BUY, "1.0", "100.00"));
        authenticateAs(sellerId);
        UUID sellerOrderId = orderPlacementPort.placeOrder(order("sell-replay", OrderSide.SELL, "1.0", "100.00"));

        MatchingEventPort adapter = matchingEventPorts.stream()
            .filter(port -> port.getClass().getName().contains("MatchingTradingEventAdapter"))
            .findFirst()
            .orElseThrow();
        adapter.executionCreated(new MatchingEventPort.ExecutionCreatedEvent(
            "execution:BTC-USD:3",
            "match:BTC-USD:3",
            "BTC-USD",
            buyerOrderId,
            sellerOrderId,
            buyerOrderId,
            sellerOrderId,
            new BigDecimal("1.0"),
            new BigDecimal("100.00"),
            3,
            2,
            2
        ));

        assertThat(countRows("trading_settlement_instructions", "execution_id = 'execution:BTC-USD:3'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference = 'trade-settlement:execution:BTC-USD:3'")).isEqualTo(1);
    }

    @Test
    void cancelBeforeCounterpartyReleasesOrderWithoutFill() {
        UUID buyerId = activeUser("matching-cancel-buyer@example.com");
        configureMarket();
        fund(buyerId, "USD", "1000.00");

        authenticateAs(buyerId);
        UUID buyerOrderId = orderPlacementPort.placeOrder(order("buy-cancel", OrderSide.BUY, "1.0", "100.00"));
        orderCancellationPort.cancelOrder(new OrderCancellationPort.CancelOrderCommand(buyerOrderId));

        assertThat(orderRepository.findById(buyerOrderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(balance("USER", buyerId.toString(), "USD", "AVAILABLE")).isEqualByComparingTo("1000.00");
        assertThat(balance("USER", buyerId.toString(), "USD", "LOCKED")).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentDuplicateSubmissionReplaysToOneBookOrder() throws Exception {
        UUID buyerId = activeUser("matching-duplicate-buyer@example.com");
        configureMarket();
        fund(buyerId, "USD", "1000.00");

        runConcurrently(4, () -> {
            authenticateAs(buyerId);
            orderPlacementPort.placeOrder(order("buy-duplicate", OrderSide.BUY, "1.0", "100.00"));
            return null;
        });

        assertThat(countRows("trading_orders", "client_order_id = 'buy-duplicate'")).isEqualTo(1);
        assertThat(countRows("matching_orders", "market_symbol = 'BTC-USD'")).isEqualTo(1);
        assertThat(countRows("matching_executions", "market_symbol = 'BTC-USD'")).isZero();
    }

    @Test
    void concurrentSameMarketMatchingSerializesWithoutDuplicateExecutions() throws Exception {
        UUID buyerOne = activeUser("matching-concurrent-buyer-1@example.com");
        UUID buyerTwo = activeUser("matching-concurrent-buyer-2@example.com");
        UUID sellerOne = activeUser("matching-concurrent-seller-1@example.com");
        UUID sellerTwo = activeUser("matching-concurrent-seller-2@example.com");
        configureMarket();
        fund(buyerOne, "USD", "1000.00");
        fund(buyerTwo, "USD", "1000.00");
        fund(sellerOne, "BTC", "1.00");
        fund(sellerTwo, "BTC", "1.00");

        authenticateAs(buyerOne);
        orderPlacementPort.placeOrder(order("buy-concurrent-1", OrderSide.BUY, "1.0", "100.00"));
        authenticateAs(buyerTwo);
        orderPlacementPort.placeOrder(order("buy-concurrent-2", OrderSide.BUY, "1.0", "100.00"));

        runConcurrently(2, List.of(
            () -> {
                authenticateAs(sellerOne);
                orderPlacementPort.placeOrder(order("sell-concurrent-1", OrderSide.SELL, "1.0", "100.00"));
                return null;
            },
            () -> {
                authenticateAs(sellerTwo);
                orderPlacementPort.placeOrder(order("sell-concurrent-2", OrderSide.SELL, "1.0", "100.00"));
                return null;
            }
        ));

        assertThat(countRows("matching_executions", "market_symbol = 'BTC-USD'")).isEqualTo(2);
        assertThat(countRows("trading_settlement_instructions", "market_symbol = 'BTC-USD'")).isEqualTo(2);
        assertThat(countRows("matching_executions", "execution_id = 'execution:BTC-USD:4'")).isEqualTo(1);
        assertThat(countRows("matching_executions", "execution_id = 'execution:BTC-USD:6'")).isEqualTo(1);
    }

    private OrderPlacementPort.PlaceOrderCommand order(String clientOrderId, OrderSide side, String quantity, String price) {
        return new OrderPlacementPort.PlaceOrderCommand(
            clientOrderId,
            "BTC-USD",
            side,
            OrderType.LIMIT,
            TimeInForce.GTC,
            new BigDecimal(quantity),
            new BigDecimal(price)
        );
    }

    private void configureMarket() {
        marketRepository.save(Market.register(
            "BTC-USD",
            "BTC",
            "USD",
            2,
            8,
            new BigDecimal("0.001"),
            new BigDecimal("10.00"),
            true,
            Clock.systemUTC().instant()
        ));
        feeScheduleRepository.save(FeeSchedule.configure(
            "BTC-USD",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            FeeAssetType.QUOTE,
            true,
            Clock.systemUTC().instant()
        ));
    }

    private UUID activeUser(String email) {
        SecurityContextHolder.clearContext();
        RegistrationResult result = registrationPort.register(new RegistrationCommand(email, "Matching User", PASSWORD, CONTEXT));
        emailVerificationPort.verify(result.emailVerificationToken(), CONTEXT);
        return result.userId();
    }

    private void fund(UUID userId, String assetCode, String amount) {
        LedgerAccountView external = ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.EXTERNAL,
            "matching:external:" + assetCode,
            assetCode,
            BalanceType.EXTERNAL
        ));
        LedgerAccountView userAvailable = ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            userId.toString(),
            assetCode,
            BalanceType.AVAILABLE
        ));
        ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "matching:fund:" + userId + ":" + assetCode + ":" + amount,
            "matching:fund:" + userId + ":" + assetCode + ":" + amount,
            "fund matching test account",
            AuditMetadata.of("test", "matching-test", userId.toString(), assetCode, "test funding"),
            List.of(
                new PostingLineCommand(external.accountId(), PostingDirection.DEBIT, new BigDecimal(amount)),
                new PostingLineCommand(userAvailable.accountId(), PostingDirection.CREDIT, new BigDecimal(amount))
            )
        ));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            userId.toString(),
            "test",
            AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private BigDecimal balance(String ownerType, String ownerId, String assetCode, String balanceType) {
        BigDecimal balance = jdbcTemplate.queryForObject(
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
        return balance == null ? BigDecimal.ZERO : balance;
    }

    private long countRows(String tableName, String whereClause) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Long.class);
        return count == null ? 0 : count;
    }

    private <T> List<T> runConcurrently(int workers, Callable<T> task) throws Exception {
        return runConcurrently(workers, java.util.stream.IntStream.range(0, workers)
            .mapToObj(ignored -> task)
            .toList());
    }

    private <T> List<T> runConcurrently(int workers, List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream()
                .map(task -> executor.submit(() -> {
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
}
