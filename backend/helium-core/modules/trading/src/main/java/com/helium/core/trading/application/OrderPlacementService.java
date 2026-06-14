package com.helium.core.trading.application;

import com.helium.core.authuser.application.AccountStatusPort;
import com.helium.core.ledger.application.FundsReservationPort;
import com.helium.core.ledger.application.FundsReservationResult;
import com.helium.core.ledger.application.ReserveFundsCommand;
import com.helium.core.matching.application.MatchingCommandPort;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.domain.OrderReservation;
import com.helium.core.trading.domain.FeeAssetType;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.OrderRepository;
import com.helium.core.trading.infrastructure.OrderReservationRepository;
import com.helium.core.trading.infrastructure.TradingSecurityContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderPlacementService implements OrderPlacementPort {
    private final OrderRepository orderRepository;
    private final MarketRepository marketRepository;
    private final OrderReservationRepository reservationRepository;
    private final AccountStatusPort accountStatusPort;
    private final FundsReservationPort fundsReservationPort;
    private final ObjectProvider<MatchingCommandPort> matchingCommandPortProvider;
    private final FeeService feeService;
    private final TradingAuditPublisher auditPublisher;
    private final TradingSecurityContext securityContext;
    private final TradingAdvisoryLockService lockService;
    private final Clock clock;

    OrderPlacementService(
        OrderRepository orderRepository,
        MarketRepository marketRepository,
        OrderReservationRepository reservationRepository,
        AccountStatusPort accountStatusPort,
        FundsReservationPort fundsReservationPort,
        ObjectProvider<MatchingCommandPort> matchingCommandPortProvider,
        FeeService feeService,
        TradingAuditPublisher auditPublisher,
        TradingSecurityContext securityContext,
        TradingAdvisoryLockService lockService,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.marketRepository = marketRepository;
        this.reservationRepository = reservationRepository;
        this.accountStatusPort = accountStatusPort;
        this.fundsReservationPort = fundsReservationPort;
        this.matchingCommandPortProvider = matchingCommandPortProvider;
        this.feeService = feeService;
        this.auditPublisher = auditPublisher;
        this.securityContext = securityContext;
        this.lockService = lockService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID placeOrder(PlaceOrderCommand command) {
        UUID userId = securityContext.requireUserId();

        lockService.lock("trading:order-placement", userId + ":" + command.clientOrderId());

        if (!accountStatusPort.isActive(userId)) {
            throw new TradingValidationException("account is not active");
        }

        String requestHash = TradingHash.orderPlacementHash(
            userId,
            command.marketSymbol(),
            command.side(),
            command.orderType(),
            command.quantity(),
            command.limitPrice(),
            command.timeInForce()
        );

        var existingOrderOpt = orderRepository.findByUserIdAndClientOrderId(userId, command.clientOrderId());
        if (existingOrderOpt.isPresent()) {
            Order existing = existingOrderOpt.get();
            if (!existing.requestHash().equals(requestHash)) {
                throw new TradingValidationException("duplicate client order id with different request hash");
            }
            return existing.id();
        }

        Market market = marketRepository.findById(command.marketSymbol())
            .orElseThrow(() -> new TradingValidationException("market not found"));

        FeeEstimate estimatedFee = feeService.estimate(market, command.side(), command.quantity(), command.limitPrice());

        Order order = Order.receive(
            userId,
            command.clientOrderId(),
            requestHash,
            command.marketSymbol(),
            command.side(),
            command.orderType(),
            command.timeInForce(),
            command.quantity(),
            command.limitPrice(),
            estimatedFee.rate(),
            estimatedFee.assetCode(),
            estimatedFee.policyVersion(),
            clock.instant()
        );

        order.validateAgainst(market, clock.instant());
        
        String assetCode;
        BigDecimal reserveAmount;

        if (order.side() == OrderSide.BUY) {
            assetCode = market.quoteAsset();
            BigDecimal notional = order.quantity().multiply(order.limitPrice());
            reserveAmount = notional.add(estimatedFee.amount());
        } else {
            assetCode = market.baseAsset();
            reserveAmount = estimatedFee.assetType() == FeeAssetType.BASE
                ? order.quantity().add(estimatedFee.amount())
                : order.quantity();
        }

        FundsReservationResult result = fundsReservationPort.reserve(new ReserveFundsCommand(
            order.userId(),
            assetCode,
            reserveAmount,
            "trading:reserve:" + order.id(),
            "trading:reserve:" + order.id(),
            order.userId().toString(),
            "order placement"
        ));

        order.markFundsReserved(clock.instant());

        OrderReservation reservation = OrderReservation.active(
            order.id(),
            assetCode,
            reserveAmount,
            estimatedFee.amount(),
            result.transactionId(),
            clock.instant()
        );

        order.markSentToMatching(clock.instant());

        orderRepository.save(order);
        reservationRepository.save(reservation);

        auditPublisher.publish(order.id(), order.status(), order.userId().toString(), "Order placed");

        MatchingCommandPort matchingCommandPort = matchingCommandPortProvider.getIfAvailable();
        if (matchingCommandPort != null) {
            matchingCommandPort.submitOrder(new MatchingCommandPort.SubmitOrderCommand(
                order.id(),
                order.marketSymbol(),
                order.side().name(),
                order.orderType().name(),
                order.timeInForce().name(),
                order.quantity(),
                order.limitPrice()
            ));
        }

        return order.id();
    }
}
