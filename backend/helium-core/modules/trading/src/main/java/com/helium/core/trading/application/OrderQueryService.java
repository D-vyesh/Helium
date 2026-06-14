package com.helium.core.trading.application;

import com.helium.core.trading.domain.Order;
import com.helium.core.trading.infrastructure.OrderRepository;
import com.helium.core.trading.infrastructure.TradingSecurityContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class OrderQueryService implements OrderQueryPort {
    private final OrderRepository repository;
    private final TradingSecurityContext securityContext;

    OrderQueryService(OrderRepository repository, TradingSecurityContext securityContext) {
        this.repository = repository;
        this.securityContext = securityContext;
    }

    @Override
    public Optional<OrderView> getOrder(UUID orderId) {
        UUID userId = securityContext.requireUserId();
        return repository.findById(orderId)
            .filter(order -> order.userId().equals(userId))
            .map(this::mapToView);
    }

    @Override
    public List<OrderView> getOrders() {
        UUID userId = securityContext.requireUserId();
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::mapToView)
            .collect(Collectors.toList());
    }

    private OrderView mapToView(Order order) {
        return new OrderView(
            order.id(),
            order.userId(),
            order.clientOrderId(),
            order.marketSymbol(),
            order.side(),
            order.orderType(),
            order.status(),
            order.timeInForce(),
            order.quantity(),
            order.limitPrice(),
            order.filledQuantity(),
            order.createdAt(),
            order.updatedAt()
        );
    }
}
