package com.helium.core.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.authuser.application.EmailVerificationPort;
import com.helium.core.authuser.application.RegistrationCommand;
import com.helium.core.authuser.application.RegistrationPort;
import com.helium.core.authuser.application.RegistrationResult;
import com.helium.core.authuser.application.SecurityContextData;
import com.helium.core.authuser.domain.AuthValidationException;
import com.helium.core.authuser.domain.Role;
import com.helium.core.ledger.application.CreateLedgerAccountCommand;
import com.helium.core.ledger.application.LedgerAccountPort;
import com.helium.core.ledger.application.LedgerAccountView;
import com.helium.core.ledger.application.LedgerPostingCommand;
import com.helium.core.ledger.application.LedgerPostingPort;
import com.helium.core.ledger.application.PostingLineCommand;
import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerInvariantViolationException;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.PostingDirection;
import com.helium.core.matching.application.TrustedMatchingActorIssuer;
import com.helium.core.matching.application.TrustedMatchingAuthentication;
import com.helium.core.trading.application.MarketAdministrationPort;
import com.helium.core.trading.application.OrderCancellationPort;
import com.helium.core.trading.application.OrderPlacementPort;
import com.helium.core.trading.application.OrderQueryPort;
import com.helium.core.trading.application.OrderStatusService;
import com.helium.core.trading.application.TradingSettlementPort;
import com.helium.core.trading.domain.FeeAssetType;
import com.helium.core.trading.domain.FeeSchedule;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.ReservationStatus;
import com.helium.core.trading.domain.TimeInForce;
import com.helium.core.trading.domain.TradingInvariantViolationException;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.FeeScheduleRepository;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.OrderRepository;
import com.helium.core.trading.infrastructure.OrderReservationRepository;
import com.helium.core.trading.infrastructure.SettlementInstructionRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = HeliumCoreApplication.class)
@Testcontainers
class TradingPostgresIntegrationTest {
    private static final SecurityContextData CONTEXT = new SecurityContextData("127.0.0.1", "trading-test");
    private static final String PASSWORD = "Initial-password-123";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegistrationPort registrationPort;

    @Autowired
    private EmailVerificationPort emailVerificationPort;

    @Autowired
    private MarketAdministrationPort marketAdministrationPort;

    @Autowired
    private OrderPlacementPort orderPlacementPort;

    @Autowired
    private OrderCancellationPort orderCancellationPort;

    @Autowired
    private OrderStatusService orderStatusService;

    @Autowired
    private OrderQueryPort orderQueryPort;

    @Autowired
    private TradingSettlementPort tradingSettlementPort;

    @Autowired
    private TrustedMatchingActorIssuer matchingActorIssuer;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private FeeScheduleRepository feeScheduleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReservationRepository reservationRepository;

    @Autowired
    private SettlementInstructionRepository settlementRepository;

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
    void placesOrderWithServerSideIdempotencyAndRealLedgerReservation() {
        UUID userId = activeUser("placement@example.com");
        configureMarket("BTC-USD", FeeAssetType.QUOTE, "0.0000");
        fund(userId, "USD", "1000.00");

        authenticateAs(userId);
        UUID first = orderPlacementPort.placeOrder(buyCommand("client-placement", "1.0", "100.00"));
        UUID replay = orderPlacementPort.placeOrder(buyCommand("client-placement", "1.0", "100.00"));

        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(() -> orderPlacementPort.placeOrder(buyCommand("client-placement", "1.0", "101.00")))
            .isInstanceOf(TradingValidationException.class)
            .hasMessageContaining("different request hash");
        assertThat(requestHash(first)).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(orderRepository.findById(first).orElseThrow().status()).isEqualTo(OrderStatus.SENT_TO_MATCHING);
        assertThat(balance("USER", userId.toString(), "USD", "AVAILABLE")).isEqualByComparingTo("900.00");
        assertThat(balance("USER", userId.toString(), "USD", "LOCKED")).isEqualByComparingTo("100.00");
    }

    @Test
    void matchingAcceptanceIsRequiredBeforeOrderOpens() {
        TradingPair pair = acceptedPair("matching-ack", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", false);

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.SENT_TO_MATCHING);

        authenticateMatching();
        orderStatusService.markAccepted(pair.buyerOrderId(), 1);

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.OPEN);
        assertThat(orderRepository.findById(pair.sellerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.SENT_TO_MATCHING);
    }

    @Test
    void pairedSettlementConservesBuyerSellerAndFeeBalances() {
        TradingPair pair = acceptedPair("paired", FeeAssetType.QUOTE, "0.0100", "1.0", "100.00", true);

        authenticateMatching();
        tradingSettlementPort.processExecution(execution("exec-paired", 2, pair, "1.0", "100.00", "1.00", "1.00"));

        assertThat(balance("USER", pair.buyerId().toString(), "USD", "AVAILABLE")).isEqualByComparingTo("899.00");
        assertThat(balance("USER", pair.buyerId().toString(), "USD", "LOCKED")).isEqualByComparingTo("0.00");
        assertThat(balance("USER", pair.buyerId().toString(), "BTC", "AVAILABLE")).isEqualByComparingTo("1.00");

        assertThat(balance("USER", pair.sellerId().toString(), "BTC", "AVAILABLE")).isEqualByComparingTo("1.00");
        assertThat(balance("USER", pair.sellerId().toString(), "BTC", "LOCKED")).isEqualByComparingTo("0.00");
        assertThat(balance("USER", pair.sellerId().toString(), "USD", "AVAILABLE")).isEqualByComparingTo("99.00");

        assertThat(balance("FEE", "trading:fee:USD", "USD", "FEE")).isEqualByComparingTo("2.00");
    }

    @Test
    void partialAndMultiFillSettlementUsesCumulativeReservationConsumption() {
        TradingPair pair = acceptedPair("multi-fill", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);

        authenticateMatching();
        tradingSettlementPort.processExecution(execution("exec-multi-1", 2, pair, "0.4", "100.00", "0.00", "0.00"));
        tradingSettlementPort.processExecution(execution("exec-multi-2", 3, pair, "0.6", "100.00", "0.00", "0.00"));

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(orderRepository.findById(pair.sellerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(settlementRepository.count()).isEqualTo(2);
        assertThat(reservationRepository.findByOrderId(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservationRepository.findByOrderId(pair.sellerOrderId()).orElseThrow().status()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void duplicateExecutionReplaysSafelyAndMismatchedReplayIsRejected() {
        TradingPair pair = acceptedPair("replay", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);

        authenticateMatching();
        TradingSettlementPort.TradeExecutionCommand command = execution("exec-replay", 2, pair, "0.5", "100.00", "0.00", "0.00");
        tradingSettlementPort.processExecution(command);
        tradingSettlementPort.processExecution(command);

        assertThat(settlementRepository.count()).isEqualTo(1);
        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().filledQuantity()).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-replay", 2, pair, "0.4", "100.00", "0.00", "0.00")))
            .isInstanceOf(TradingValidationException.class)
            .hasMessageContaining("payload differs");
    }

    @Test
    void concurrentDuplicateSettlementReplaysSafely() throws Exception {
        TradingPair pair = acceptedPair("concurrent", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);
        TradingSettlementPort.TradeExecutionCommand command = execution("exec-concurrent", 2, pair, "0.5", "100.00", "0.00", "0.00");

        runConcurrently(6, () -> {
            authenticateMatching();
            tradingSettlementPort.processExecution(command);
            return null;
        });

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().filledQuantity()).isEqualByComparingTo("0.5");
        assertThat(countRows("trading_settlement_instructions", "execution_id = 'exec-concurrent'")).isEqualTo(1);
        assertThat(countRows("ledger_transactions", "business_reference = 'trade-settlement:exec-concurrent'")).isEqualTo(1);
    }

    @Test
    void rejectsSettlementAndMatchingStatusFromUsersAdminsAndFakeMatchingPrincipals() {
        UUID adminId = activeUser("admin-settlement@example.com");
        bootstrapRole(adminId, Role.ADMIN);
        TradingPair pair = acceptedPair("auth", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);

        authenticateAs(pair.buyerId());
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-auth-user", 2, pair, "0.5", "100.00", "0.00", "0.00")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trusted matching actor");
        assertThatThrownBy(() -> orderStatusService.markRejected(pair.buyerOrderId(), "reject", 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trusted matching actor");

        authenticateAs(adminId);
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-auth-admin", 2, pair, "0.5", "100.00", "0.00", "0.00")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trusted matching actor");

        authenticateFakeMatchingPrincipal(false);
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-auth-fake", 2, pair, "0.5", "100.00", "0.00", "0.00")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trusted matching actor");

        authenticateFakeMatchingPrincipal(true);
        assertThatThrownBy(() -> orderStatusService.markRejected(pair.buyerOrderId(), "reject", 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trusted matching actor");

        assertThat(settlementRepository.count()).isZero();
        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().filledQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsOutOfOrderExecutionAndMarketMismatch() {
        TradingPair pair = acceptedPair("ordering", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);

        authenticateMatching();
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-seq-3", 3, pair, "0.5", "100.00", "0.00", "0.00")))
            .isInstanceOf(TradingValidationException.class)
            .hasMessageContaining("out of order");

        assertThatThrownBy(() -> tradingSettlementPort.processExecution(new TradingSettlementPort.TradeExecutionCommand(
            "exec-market-mismatch",
            2,
            pair.buyerOrderId(),
            pair.sellerOrderId(),
            "ETH-USD",
            new BigDecimal("0.5"),
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        ))).isInstanceOf(TradingValidationException.class)
            .hasMessageContaining("market does not match");

        assertThat(settlementRepository.count()).isZero();
    }

    @Test
    void cancelConfirmationCannotJumpAheadOfEarlierExecution() {
        TradingPair pair = acceptedPair("cancel-race", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);

        authenticateAs(pair.buyerId());
        orderCancellationPort.cancelOrder(new OrderCancellationPort.CancelOrderCommand(pair.buyerOrderId()));
        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.CANCEL_REQUESTED);

        authenticateMatching();
        assertThatThrownBy(() -> orderStatusService.markCancelled(pair.buyerOrderId(), 3))
            .isInstanceOf(TradingValidationException.class)
            .hasMessageContaining("out of order");

        tradingSettlementPort.processExecution(execution("exec-cancel-race", 2, pair, "0.4", "100.00", "0.00", "0.00"));
        orderStatusService.markCancelled(pair.buyerOrderId(), 3);

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().filledQuantity()).isEqualByComparingTo("0.4");
        assertThat(balance("USER", pair.buyerId().toString(), "USD", "AVAILABLE")).isEqualByComparingTo("960.00");
        assertThat(balance("USER", pair.buyerId().toString(), "USD", "LOCKED")).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeReleaseFailsClosedBeforeLedgerMutation() {
        TradingPair pair = acceptedPair("negative-release", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);

        authenticateMatching();
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-negative", 2, pair, "1.0", "101.00", "0.00", "0.00")))
            .isInstanceOf(TradingInvariantViolationException.class)
            .hasMessageContaining("Consumed amount exceeds reservation");

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.OPEN);
        assertThat(settlementRepository.count()).isZero();
        assertThat(reservationRepository.findByOrderId(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(balance("USER", pair.buyerId().toString(), "USD", "LOCKED")).isEqualByComparingTo("100.00");
        assertThat(countRows("ledger_transactions", "business_reference = 'trade-settlement:exec-negative'")).isZero();
    }

    @Test
    void sellReservationIncludesBaseFeeAndSettlementConsumesFee() {
        TradingPair pair = acceptedPair("base-fee", FeeAssetType.BASE, "0.0100", "1.0", "100.00", true);

        assertThat(balance("USER", pair.sellerId().toString(), "BTC", "AVAILABLE")).isEqualByComparingTo("0.99");
        assertThat(balance("USER", pair.sellerId().toString(), "BTC", "LOCKED")).isEqualByComparingTo("1.01");

        authenticateMatching();
        tradingSettlementPort.processExecution(execution("exec-base-fee", 2, pair, "1.0", "100.00", "1.00", "0.01"));

        assertThat(balance("USER", pair.sellerId().toString(), "BTC", "LOCKED")).isEqualByComparingTo("0.00");
        assertThat(balance("USER", pair.sellerId().toString(), "USD", "AVAILABLE")).isEqualByComparingTo("100.00");
        assertThat(balance("FEE", "trading:fee:BTC", "BTC", "FEE")).isEqualByComparingTo("0.01");
        assertThat(balance("FEE", "trading:fee:USD", "USD", "FEE")).isEqualByComparingTo("1.00");
    }

    @Test
    void feeScheduleChangesAffectOnlyNewOrders() {
        TradingPair pair = acceptedPair("fee-freeze", FeeAssetType.QUOTE, "0.0100", "1.0", "100.00", true);
        FeeSchedule schedule = feeScheduleRepository.findByMarketSymbol("BTC-USD").orElseThrow();
        schedule.update(BigDecimal.ZERO, new BigDecimal("0.0200"), FeeAssetType.QUOTE, true, Clock.systemUTC().instant());
        feeScheduleRepository.save(schedule);

        authenticateMatching();
        tradingSettlementPort.processExecution(execution("exec-fee-freeze", 2, pair, "1.0", "100.00", "1.00", "1.00"));

        assertThat(balance("FEE", "trading:fee:USD", "USD", "FEE")).isEqualByComparingTo("2.00");
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-fee-new", 3, pair, "0.1", "100.00", "0.20", "0.20")))
            .isInstanceOf(TradingValidationException.class);
    }

    @Test
    void ledgerFailureRollsBackOrderAndSettlement() {
        TradingPair pair = acceptedPair("ledger-failure", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);
        zeroLockedBalance(pair.buyerId(), "USD");

        authenticateMatching();
        assertThatThrownBy(() -> tradingSettlementPort.processExecution(execution("exec-ledger-failure", 2, pair, "0.5", "100.00", "0.00", "0.00")))
            .isInstanceOf(LedgerInvariantViolationException.class);

        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().status()).isEqualTo(OrderStatus.OPEN);
        assertThat(orderRepository.findById(pair.buyerOrderId()).orElseThrow().filledQuantity()).isEqualByComparingTo("0");
        assertThat(settlementRepository.count()).isZero();
    }

    @Test
    void crossUserOrderAccessAndCancellationAreDenied() {
        TradingPair pair = acceptedPair("cross-user", FeeAssetType.QUOTE, "0.0000", "1.0", "100.00", true);
        UUID otherId = activeUser("cross-user-other@example.com");

        authenticateAs(otherId);

        assertThat(orderQueryPort.getOrder(pair.buyerOrderId())).isEmpty();
        assertThatThrownBy(() -> orderCancellationPort.cancelOrder(new OrderCancellationPort.CancelOrderCommand(pair.buyerOrderId())))
            .isInstanceOf(TradingValidationException.class)
            .hasMessageContaining("does not belong");
    }

    @Test
    void deniesMarketAdministrationWithoutAdminRole() {
        UUID userId = activeUser("not-admin@example.com");
        authenticateAs(userId);

        assertThatThrownBy(() -> marketAdministrationPort.registerMarket(new MarketAdministrationPort.RegisterMarketCommand(
            "ETH-USD",
            "ETH",
            "USD",
            2,
            8,
            new BigDecimal("0.01"),
            new BigDecimal("10.00"),
            true
        ))).isInstanceOf(AuthValidationException.class);
    }

    private TradingPair acceptedPair(String suffix, FeeAssetType sellFeeAsset, String takerFeeRate, String quantity, String price, boolean accept) {
        UUID buyerId = activeUser("buyer-" + suffix + "@example.com");
        UUID sellerId = activeUser("seller-" + suffix + "@example.com");
        configureMarket("BTC-USD", sellFeeAsset, takerFeeRate);
        fund(buyerId, "USD", "1000.00");
        fund(sellerId, "BTC", "2.00");

        authenticateAs(buyerId);
        UUID buyerOrderId = orderPlacementPort.placeOrder(buyCommand("buy-" + suffix, quantity, price));
        authenticateAs(sellerId);
        UUID sellerOrderId = orderPlacementPort.placeOrder(sellCommand("sell-" + suffix, quantity, price));

        TradingPair pair = new TradingPair(buyerId, sellerId, buyerOrderId, sellerOrderId);
        if (accept) {
            authenticateMatching();
            orderStatusService.markAccepted(buyerOrderId, 1);
            orderStatusService.markAccepted(sellerOrderId, 1);
        }
        return pair;
    }

    private OrderPlacementPort.PlaceOrderCommand buyCommand(String clientOrderId, String quantity, String price) {
        return new OrderPlacementPort.PlaceOrderCommand(
            clientOrderId,
            "BTC-USD",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            new BigDecimal(quantity),
            new BigDecimal(price)
        );
    }

    private OrderPlacementPort.PlaceOrderCommand sellCommand(String clientOrderId, String quantity, String price) {
        return new OrderPlacementPort.PlaceOrderCommand(
            clientOrderId,
            "BTC-USD",
            OrderSide.SELL,
            OrderType.LIMIT,
            TimeInForce.GTC,
            new BigDecimal(quantity),
            new BigDecimal(price)
        );
    }

    private TradingSettlementPort.TradeExecutionCommand execution(
        String executionId,
        long matchingOffset,
        TradingPair pair,
        String quantity,
        String price,
        String buyerFee,
        String sellerFee
    ) {
        return new TradingSettlementPort.TradeExecutionCommand(
            executionId,
            matchingOffset,
            pair.buyerOrderId(),
            pair.sellerOrderId(),
            "BTC-USD",
            new BigDecimal(quantity),
            new BigDecimal(price),
            new BigDecimal(buyerFee),
            new BigDecimal(sellerFee)
        );
    }

    private void configureMarket(String symbol, FeeAssetType sellFeeAsset, String takerFeeRate) {
        if (marketRepository.existsById(symbol)) {
            return;
        }
        marketRepository.save(Market.register(
            symbol,
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
            symbol,
            BigDecimal.ZERO,
            new BigDecimal(takerFeeRate),
            sellFeeAsset,
            true,
            Clock.systemUTC().instant()
        ));
    }

    private UUID activeUser(String email) {
        SecurityContextHolder.clearContext();
        RegistrationResult result = registrationPort.register(new RegistrationCommand(email, "Trading User", PASSWORD, CONTEXT));
        emailVerificationPort.verify(result.emailVerificationToken(), CONTEXT);
        return result.userId();
    }

    private void fund(UUID userId, String assetCode, String amount) {
        LedgerAccountView external = ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.EXTERNAL,
            "test:external:" + assetCode,
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
            "test:fund:" + userId + ":" + assetCode + ":" + amount,
            "test:fund:" + userId + ":" + assetCode + ":" + amount,
            "fund trading test account",
            AuditMetadata.of("test", "trading-test", userId.toString(), assetCode, "test funding"),
            List.of(
                new PostingLineCommand(external.accountId(), PostingDirection.DEBIT, new BigDecimal(amount)),
                new PostingLineCommand(userAvailable.accountId(), PostingDirection.CREDIT, new BigDecimal(amount))
            )
        ));
    }

    private void zeroLockedBalance(UUID userId, String assetCode) {
        jdbcTemplate.update(
            """
            update ledger_balance_snapshots snapshot
            set current_balance = 0
            from ledger_accounts account
            where account.id = snapshot.account_id
              and account.owner_type = 'USER'
              and account.owner_id = ?
              and account.asset_code = ?
              and account.balance_type = 'LOCKED'
            """,
            userId.toString(),
            assetCode
        );
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            userId.toString(),
            "test",
            AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private void authenticateMatching() {
        SecurityContextHolder.getContext().setAuthentication(
            matchingActorIssuer.issueMatchingActor("local-dev-matching-permission")
        );
    }

    private void authenticateFakeMatchingPrincipal(boolean withAuthority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            TrustedMatchingAuthentication.MATCHING_ACTOR_ID,
            "test",
            withAuthority ? AuthorityUtils.createAuthorityList("SYSTEM_MATCHING_ENGINE") : AuthorityUtils.NO_AUTHORITIES
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

    private String requestHash(UUID orderId) {
        return jdbcTemplate.queryForObject("select request_hash from trading_orders where id = ?", String.class, orderId);
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

    private record TradingPair(UUID buyerId, UUID sellerId, UUID buyerOrderId, UUID sellerOrderId) {
    }
}
