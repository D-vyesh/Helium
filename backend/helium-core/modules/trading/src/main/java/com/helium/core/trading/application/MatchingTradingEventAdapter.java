package com.helium.core.trading.application;

import com.helium.core.matching.application.MatchingEventPort;
import com.helium.core.matching.application.MatchingExecutionLookupPort;
import com.helium.core.matching.application.TrustedMatchingActorIssuer;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class MatchingTradingEventAdapter implements MatchingEventPort {
    private final TrustedMatchingActorIssuer matchingActorIssuer;
    private final String matchingPermission;
    private final OrderStatusService orderStatusService;
    private final OrderExpirationService orderExpirationService;
    private final TradingSettlementPort tradingSettlementPort;
    private final MatchingExecutionLookupPort matchingExecutionLookupPort;
    private final OrderRepository orderRepository;
    private final MarketRepository marketRepository;

    MatchingTradingEventAdapter(
        TrustedMatchingActorIssuer matchingActorIssuer,
        @Value("${helium.matching.actor-permission:local-dev-matching-permission}") String matchingPermission,
        OrderStatusService orderStatusService,
        OrderExpirationService orderExpirationService,
        TradingSettlementPort tradingSettlementPort,
        MatchingExecutionLookupPort matchingExecutionLookupPort,
        OrderRepository orderRepository,
        MarketRepository marketRepository
    ) {
        this.matchingActorIssuer = matchingActorIssuer;
        this.matchingPermission = matchingPermission;
        this.orderStatusService = orderStatusService;
        this.orderExpirationService = orderExpirationService;
        this.tradingSettlementPort = tradingSettlementPort;
        this.matchingExecutionLookupPort = matchingExecutionLookupPort;
        this.orderRepository = orderRepository;
        this.marketRepository = marketRepository;
    }

    @Override
    public void orderAccepted(OrderAcceptedEvent event) {
        withMatchingActor(() -> orderStatusService.markAccepted(event.orderId(), event.orderOffset()));
    }

    @Override
    public void orderCancelled(OrderCancelledEvent event) {
        withMatchingActor(() -> orderStatusService.markCancelled(event.orderId(), event.orderOffset()));
    }

    @Override
    public void orderExpired(OrderExpiredEvent event) {
        withMatchingActor(() -> orderExpirationService.expireOrder(event.orderId(), event.orderOffset()));
    }

    @Override
    public void executionCreated(ExecutionCreatedEvent event) {
        verifyPersistedExecution(event);
        Order buyer = orderRepository.findById(event.buyerOrderId())
            .orElseThrow(() -> new TradingValidationException("buyer order not found"));
        Order seller = orderRepository.findById(event.sellerOrderId())
            .orElseThrow(() -> new TradingValidationException("seller order not found"));
        Market market = marketRepository.findById(event.marketSymbol())
            .orElseThrow(() -> new TradingValidationException("market not found"));
        BigDecimal buyerFee = feeFor(buyer, event.quantity(), event.price(), market);
        BigDecimal sellerFee = feeFor(seller, event.quantity(), event.price(), market);
        withMatchingActor(() -> tradingSettlementPort.processExecution(new TradingSettlementPort.TradeExecutionCommand(
            event.executionId(),
            event.sequence(),
            event.buyerOrderOffset(),
            event.sellerOrderOffset(),
            event.buyerOrderId(),
            event.sellerOrderId(),
            event.marketSymbol(),
            event.quantity(),
            event.price(),
            buyerFee,
            sellerFee
        )));
    }

    private void verifyPersistedExecution(ExecutionCreatedEvent event) {
        var execution = matchingExecutionLookupPort.findByExecutionId(event.executionId())
            .orElseThrow(() -> new TradingValidationException("matching execution not found"));
        if (!execution.marketSymbol().equals(event.marketSymbol())
            || !execution.buyerOrderId().equals(event.buyerOrderId())
            || !execution.sellerOrderId().equals(event.sellerOrderId())
            || !execution.makerOrderId().equals(event.makerOrderId())
            || !execution.takerOrderId().equals(event.takerOrderId())
            || execution.quantity().compareTo(event.quantity()) != 0
            || execution.price().compareTo(event.price()) != 0
            || execution.sequence() != event.sequence()
            || execution.buyerOrderOffset() != event.buyerOrderOffset()
            || execution.sellerOrderOffset() != event.sellerOrderOffset()) {
            throw new TradingValidationException("matching execution event does not match persisted execution");
        }
    }

    private BigDecimal feeFor(Order order, BigDecimal quantity, BigDecimal price, Market market) {
        if (order.feeAssetCode().equals(market.baseAsset())) {
            return quantity.multiply(order.feeRate()).setScale(18, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        if (order.feeAssetCode().equals(market.quoteAsset())) {
            return quantity.multiply(price).multiply(order.feeRate()).setScale(18, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        throw new TradingValidationException("order fee asset does not belong to market");
    }

    private void withMatchingActor(Runnable operation) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(matchingActorIssuer.issueMatchingActor(matchingPermission));
            operation.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
