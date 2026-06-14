package com.helium.core.trading.application;

import com.helium.core.ledger.application.FundsReservationPort;
import com.helium.core.matching.application.MatchingCommandPort;
import com.helium.core.matching.application.TrustedTradingActorIssuer;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.OrderRepository;
import com.helium.core.trading.infrastructure.OrderReservationRepository;
import com.helium.core.trading.infrastructure.TradingSecurityContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderCancellationService implements OrderCancellationPort {
    private final OrderRepository orderRepository;
    private final OrderReservationRepository reservationRepository;
    private final FundsReservationPort fundsReservationPort;
    private final ObjectProvider<MatchingCommandPort> matchingCommandPortProvider;
    private final TrustedTradingActorIssuer tradingActorIssuer;
    private final String tradingPermission;
    private final TradingAuditPublisher auditPublisher;
    private final TradingSecurityContext securityContext;
    private final Clock clock;

    OrderCancellationService(
        OrderRepository orderRepository,
        OrderReservationRepository reservationRepository,
        FundsReservationPort fundsReservationPort,
        ObjectProvider<MatchingCommandPort> matchingCommandPortProvider,
        TrustedTradingActorIssuer tradingActorIssuer,
        @Value("${helium.trading.actor-permission:local-dev-trading-permission}") String tradingPermission,
        TradingAuditPublisher auditPublisher,
        TradingSecurityContext securityContext,
        Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.fundsReservationPort = fundsReservationPort;
        this.matchingCommandPortProvider = matchingCommandPortProvider;
        this.tradingActorIssuer = tradingActorIssuer;
        this.tradingPermission = tradingPermission;
        this.auditPublisher = auditPublisher;
        this.securityContext = securityContext;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void cancelOrder(CancelOrderCommand command) {
        UUID userId = securityContext.requireUserId();
        Order order = orderRepository.findByIdForUpdate(command.orderId())
            .orElseThrow(() -> new TradingValidationException("order not found"));

        if (!order.userId().equals(userId)) {
            throw new TradingValidationException("order does not belong to user");
        }

        order.startCancellation(clock.instant());
        orderRepository.save(order);

        auditPublisher.publish(order.id(), order.status(), userId.toString(), "Cancellation requested");

        MatchingCommandPort matchingCommandPort = matchingCommandPortProvider.getIfAvailable();
        if (matchingCommandPort != null) {
            withTradingActor(() -> matchingCommandPort.cancelOrder(new MatchingCommandPort.CancelOrderCommand(
                order.id(),
                order.marketSymbol()
            )));
        }
    }

    private void withTradingActor(Runnable operation) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(tradingActorIssuer.issueTradingActor(tradingPermission));
            operation.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
