package com.helium.core.trading.application;

import com.helium.core.ledger.application.LedgerTradeSettlementCommand;
import com.helium.core.ledger.application.TradeSettlementPort;
import com.helium.core.matching.application.TrustedMatchingActorProvider;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.domain.OrderReservation;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.ReservationStatus;
import com.helium.core.trading.domain.TradeSettlementInstruction;
import com.helium.core.trading.domain.TradingInvariantViolationException;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.OrderRepository;
import com.helium.core.trading.infrastructure.OrderReservationRepository;
import com.helium.core.trading.infrastructure.SettlementInstructionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementCoordinator implements TradingSettlementPort {
    private final OrderRepository orderRepository;
    private final OrderReservationRepository reservationRepository;
    private final SettlementInstructionRepository settlementInstructionRepository;
    private final MarketRepository marketRepository;
    private final TradeSettlementPort ledgerSettlementPort;
    private final TrustedMatchingActorProvider matchingActorProvider;
    private final TradingAdvisoryLockService lockService;
    private final ReservationReleaseService releaseService;
    private final TradingAuditPublisher auditPublisher;
    private final Clock clock;

    SettlementCoordinator(
        OrderRepository orderRepository,
        OrderReservationRepository reservationRepository,
        SettlementInstructionRepository settlementInstructionRepository,
        MarketRepository marketRepository,
        TradeSettlementPort ledgerSettlementPort,
        TrustedMatchingActorProvider matchingActorProvider,
        TradingAdvisoryLockService lockService,
        ReservationReleaseService releaseService,
        TradingAuditPublisher auditPublisher,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.settlementInstructionRepository = settlementInstructionRepository;
        this.marketRepository = marketRepository;
        this.ledgerSettlementPort = ledgerSettlementPort;
        this.matchingActorProvider = matchingActorProvider;
        this.lockService = lockService;
        this.releaseService = releaseService;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExecution(TradeExecutionCommand command) {
        matchingActorProvider.requireMatchingEngine();
        String actorId = matchingActorProvider.matchingActorId();
        lockService.lock("trading:execution", "execution:" + command.executionId());

        String executionHash = TradingHash.executionHash(
            command.executionId(),
            command.marketSequence(),
            command.buyerOrderOffset(),
            command.sellerOrderOffset(),
            command.buyerOrderId(),
            command.sellerOrderId(),
            command.marketSymbol(),
            command.quantity(),
            command.price(),
            command.buyerFee(),
            command.sellerFee()
        );
        var existing = settlementInstructionRepository.findByExecutionId(command.executionId());
        if (existing.isPresent()) {
            if (!existing.get().executionHash().equals(executionHash)) {
                throw new TradingValidationException("execution replay payload differs");
            }
            return;
        }

        OrderPair orders = lockOrders(command.buyerOrderId(), command.sellerOrderId());
        Order buyer = orders.buyer();
        Order seller = orders.seller();

        validateExecution(command, buyer, seller);
        validateLimitPrices(command, buyer, seller);

        Market market = marketRepository.findById(buyer.marketSymbol())
            .orElseThrow(() -> new TradingValidationException("market not found"));
        BigDecimal buyerExpectedFee = expectedFee(buyer, command.quantity(), command.price(), market);
        BigDecimal sellerExpectedFee = expectedFee(seller, command.quantity(), command.price(), market);
        if (command.buyerFee().compareTo(buyerExpectedFee) != 0) {
            throw new TradingValidationException("buyer execution fee does not match frozen order fee terms");
        }
        if (command.sellerFee().compareTo(sellerExpectedFee) != 0) {
            throw new TradingValidationException("seller execution fee does not match frozen order fee terms");
        }

        BigDecimal buyerReserveConsumedAmount = buyerReserveConsumedAmount(buyer, command.quantity(), command.price(), command.buyerFee(), market);
        BigDecimal sellerReserveConsumedAmount = sellerReserveConsumedAmount(seller, command.quantity(), command.sellerFee(), market);
        validateReservationCapacity(buyer, buyerReserveConsumedAmount);
        validateReservationCapacity(seller, sellerReserveConsumedAmount);

        buyer.applyFill(command.quantity(), command.buyerOrderOffset(), clock.instant());
        seller.applyFill(command.quantity(), command.sellerOrderOffset(), clock.instant());
        orderRepository.save(buyer);
        orderRepository.save(seller);

        var ledgerResult = ledgerSettlementPort.settle(new LedgerTradeSettlementCommand(
            command.executionId(),
            buyer.id(),
            seller.id(),
            buyer.userId(),
            seller.userId(),
            buyer.marketSymbol(),
            market.baseAsset(),
            market.quoteAsset(),
            command.quantity(),
            command.price(),
            command.buyerFee(),
            buyer.feeAssetCode(),
            command.sellerFee(),
            seller.feeAssetCode(),
            "trading:settlement:" + command.executionId(),
            actorId
        ));

        TradeSettlementInstruction instruction = TradeSettlementInstruction.pending(
            command.executionId(),
            executionHash,
            command.marketSequence(),
            buyer.id(),
            seller.id(),
            buyer.marketSymbol(),
            command.quantity(),
            command.price(),
            command.buyerFee(),
            buyer.feeAssetCode(),
            command.sellerFee(),
            seller.feeAssetCode(),
            buyerReserveConsumedAmount,
            sellerReserveConsumedAmount,
            clock.instant()
        );
        instruction.markSettled(ledgerResult.transactionId(), clock.instant());
        settlementInstructionRepository.save(instruction);

        releaseIfTerminal(buyer, actorId);
        releaseIfTerminal(seller, actorId);

        auditPublisher.publish(buyer.id(), buyer.status(), actorId, "Trade executed: " + command.quantity() + " @ " + command.price());
        auditPublisher.publish(seller.id(), seller.status(), actorId, "Trade executed: " + command.quantity() + " @ " + command.price());
    }

    private OrderPair lockOrders(UUID buyerOrderId, UUID sellerOrderId) {
        if (buyerOrderId.equals(sellerOrderId)) {
            throw new TradingValidationException("buyer and seller orders must differ");
        }
        UUID firstId = buyerOrderId.toString().compareTo(sellerOrderId.toString()) <= 0 ? buyerOrderId : sellerOrderId;
        UUID secondId = firstId.equals(buyerOrderId) ? sellerOrderId : buyerOrderId;

        Order first = orderRepository.findByIdForUpdate(firstId)
            .orElseThrow(() -> new TradingValidationException("order not found"));
        Order second = orderRepository.findByIdForUpdate(secondId)
            .orElseThrow(() -> new TradingValidationException("order not found"));

        Order buyer = first.id().equals(buyerOrderId) ? first : second;
        Order seller = first.id().equals(sellerOrderId) ? first : second;
        return new OrderPair(buyer, seller);
    }

    private static void validateExecution(TradeExecutionCommand command, Order buyer, Order seller) {
        if (buyer.side() != OrderSide.BUY) {
            throw new TradingValidationException("buyer order must be BUY");
        }
        if (seller.side() != OrderSide.SELL) {
            throw new TradingValidationException("seller order must be SELL");
        }
        String marketSymbol = Market.normalizeSymbol(command.marketSymbol());
        if (!buyer.marketSymbol().equals(marketSymbol) || !seller.marketSymbol().equals(marketSymbol)) {
            throw new TradingValidationException("execution market does not match order market");
        }
        if (!buyer.marketSymbol().equals(seller.marketSymbol())) {
            throw new TradingValidationException("buyer and seller markets differ");
        }
        Market.requirePositive(command.quantity(), "quantity");
        Market.requirePositive(command.price(), "price");
        Market.requireNonNegative(command.buyerFee(), "buyerFee");
        Market.requireNonNegative(command.sellerFee(), "sellerFee");
    }

    private static void validateLimitPrices(TradeExecutionCommand command, Order buyer, Order seller) {
        if (buyer.limitPrice() == null || command.price().compareTo(buyer.limitPrice()) > 0) {
            throw new TradingValidationException("execution price exceeds buyer limit price");
        }
        if (seller.limitPrice() == null || command.price().compareTo(seller.limitPrice()) < 0) {
            throw new TradingValidationException("execution price is below seller limit price");
        }
    }

    private static BigDecimal expectedFee(Order order, BigDecimal quantity, BigDecimal price, Market market) {
        if (order.feeAssetCode().equals(market.baseAsset())) {
            return quantity.multiply(order.feeRate()).setScale(18, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        if (order.feeAssetCode().equals(market.quoteAsset())) {
            return quantity.multiply(price).multiply(order.feeRate()).setScale(18, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        throw new TradingValidationException("frozen fee asset is not part of market");
    }

    private static BigDecimal buyerReserveConsumedAmount(Order buyer, BigDecimal quantity, BigDecimal price, BigDecimal feeAmount, Market market) {
        BigDecimal notional = quantity.multiply(price);
        return buyer.feeAssetCode().equals(market.quoteAsset())
            ? notional.add(feeAmount).stripTrailingZeros()
            : notional.stripTrailingZeros();
    }

    private static BigDecimal sellerReserveConsumedAmount(Order seller, BigDecimal quantity, BigDecimal feeAmount, Market market) {
        return seller.feeAssetCode().equals(market.baseAsset())
            ? quantity.add(feeAmount).stripTrailingZeros()
            : quantity.stripTrailingZeros();
    }

    private void validateReservationCapacity(Order order, BigDecimal currentConsumedAmount) {
        OrderReservation reservation = reservationRepository.findByOrderIdForUpdate(order.id())
            .orElseThrow(() -> new TradingInvariantViolationException("Order reservation is missing"));
        if (reservation.status() != ReservationStatus.ACTIVE) {
            throw new TradingInvariantViolationException("Order reservation is not active");
        }
        BigDecimal previousConsumed = settlementInstructionRepository.sumReserveConsumedAmountByOrderId(order.id());
        BigDecimal totalConsumed = previousConsumed.add(currentConsumedAmount).stripTrailingZeros();
        if (reservation.reservedAmount().subtract(totalConsumed).signum() < 0) {
            throw new TradingInvariantViolationException("Consumed amount exceeds reservation");
        }
    }

    private void releaseIfTerminal(Order order, String actorId) {
        if (order.terminal()) {
            releaseService.releaseRemaining(
                order,
                "trade execution completion",
                "trading:release:" + order.id() + ":settlement",
                actorId
            );
        }
    }

    private record OrderPair(Order buyer, Order seller) {
    }
}
