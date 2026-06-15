package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.trading.application.OrderCancellationPort;
import com.helium.core.trading.application.OrderPlacementPort;
import com.helium.core.trading.application.OrderQueryPort;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderStatus;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TimeInForce;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Trading")
public class TradingApiController {
    private final TrustedActorProvider trustedActorProvider;
    private final OrderPlacementPort orderPlacementPort;
    private final OrderCancellationPort orderCancellationPort;
    private final OrderQueryPort orderQueryPort;
    private final ApiReadService readService;

    public TradingApiController(
        TrustedActorProvider trustedActorProvider,
        OrderPlacementPort orderPlacementPort,
        OrderCancellationPort orderCancellationPort,
        OrderQueryPort orderQueryPort,
        ApiReadService readService
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.orderPlacementPort = orderPlacementPort;
        this.orderCancellationPort = orderCancellationPort;
        this.orderQueryPort = orderQueryPort;
        this.readService = readService;
    }

    @PostMapping("/orders")
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        UUID orderId = orderPlacementPort.placeOrder(new OrderPlacementPort.PlaceOrderCommand(
            request.clientOrderId(),
            request.market(),
            request.side(),
            request.type(),
            request.timeInForce(),
            request.quantity(),
            request.price()
        ));
        return new OrderResponse(orderId);
    }

    @DeleteMapping("/orders/{id}")
    public void cancelOrder(@PathVariable UUID id) {
        orderCancellationPort.cancelOrder(new OrderCancellationPort.CancelOrderCommand(id));
    }

    @GetMapping("/orders/open")
    public List<OrderQueryPort.OrderView> openOrders() {
        UUID userId = requireUserId();
        return orderQueryPort.getOrders().stream()
            .filter(order -> order.userId().equals(userId))
            .filter(order -> order.status() == OrderStatus.OPEN || order.status() == OrderStatus.PARTIALLY_FILLED)
            .toList();
    }

    @GetMapping("/orders/history")
    public List<OrderQueryPort.OrderView> orderHistory() {
        UUID userId = requireUserId();
        return orderQueryPort.getOrders().stream()
            .filter(order -> order.userId().equals(userId))
            .toList();
    }

    @GetMapping("/trades/history")
    public List<ApiReadService.TradeDto> tradeHistory() {
        return readService.tradeHistory(requireUserId());
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    public record PlaceOrderRequest(
        @NotBlank @Size(max = 120) String clientOrderId,
        @NotBlank @Size(max = 80) String market,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @NotNull TimeInForce timeInForce,
        @DecimalMin(value = "0.000000000000000001") BigDecimal quantity,
        @DecimalMin(value = "0.000000000000000001") BigDecimal price
    ) {}

    public record OrderResponse(UUID orderId) {}
}
