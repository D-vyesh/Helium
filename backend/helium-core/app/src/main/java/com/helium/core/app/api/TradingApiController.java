package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.trading.application.OrderCancellationPort;
import com.helium.core.trading.application.OrderPlacementPort;
import com.helium.core.trading.application.OrderPreviewPort;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private final OrderPreviewPort orderPreviewPort;
    private final ApiReadService readService;

    public TradingApiController(
        TrustedActorProvider trustedActorProvider,
        OrderPlacementPort orderPlacementPort,
        OrderCancellationPort orderCancellationPort,
        OrderQueryPort orderQueryPort,
        OrderPreviewPort orderPreviewPort,
        ApiReadService readService
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.orderPlacementPort = orderPlacementPort;
        this.orderCancellationPort = orderCancellationPort;
        this.orderQueryPort = orderQueryPort;
        this.orderPreviewPort = orderPreviewPort;
        this.readService = readService;
    }

    @PostMapping("/orders")
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        UUID orderId = orderPlacementPort.placeOrder(new OrderPlacementPort.PlaceOrderCommand(
            request.clientOrderId(),
            internalMarketSymbol(request.market()),
            request.side(),
            request.type(),
            request.timeInForce(),
            request.quantity(),
            request.price(),
            request.stopPrice()
        ));
        return new OrderResponse(orderId);
    }

    @PostMapping("/orders/preview")
    public OrderPreviewResponse previewOrder(@Valid @RequestBody PreviewOrderRequest request) {
        return mapPreview(orderPreviewPort.preview(new OrderPreviewPort.PreviewOrderCommand(
            internalMarketSymbol(request.market()),
            request.side(),
            request.type(),
            request.timeInForce(),
            request.quantity(),
            request.price()
        )));
    }

    @DeleteMapping("/orders/{id}")
    public void cancelOrder(@PathVariable UUID id) {
        orderCancellationPort.cancelOrder(new OrderCancellationPort.CancelOrderCommand(id));
    }

    @GetMapping("/orders/open")
    public List<OrderViewResponse> openOrders() {
        UUID userId = requireUserId();
        return orderQueryPort.getOrders().stream()
            .filter(order -> order.userId().equals(userId))
            .filter(order -> !order.status().terminal())
            .map(this::mapOrder)
            .toList();
    }

    @GetMapping("/orders/history")
    public List<OrderViewResponse> orderHistory() {
        UUID userId = requireUserId();
        return orderQueryPort.getOrders().stream()
            .filter(order -> order.userId().equals(userId))
            .map(this::mapOrder)
            .toList();
    }

    @GetMapping("/orders/{id}")
    public OrderViewResponse order(@PathVariable UUID id) {
        return orderQueryPort.getOrder(id)
            .map(this::mapOrder)
            .orElseThrow(() -> new ApiForbiddenException("order not found"));
    }

    @GetMapping("/trades/history")
    public List<TradeHistoryResponse> tradeHistory() {
        return readService.tradeHistory(requireUserId()).stream()
            .map(trade -> new TradeHistoryResponse(
                trade.executionId(),
                externalMarketSymbol(trade.market()),
                trade.side(),
                trade.price(),
                trade.quantity(),
                trade.fee(),
                trade.time()
            ))
            .toList();
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    private OrderViewResponse mapOrder(OrderQueryPort.OrderView order) {
        ApiReadService.OrderExecutionSummary summary = readService.orderExecutionSummary(order.id());
        BigDecimal remaining = order.quantity().subtract(order.filledQuantity()).stripTrailingZeros();
        return new OrderViewResponse(
            order.id(),
            order.userId(),
            order.clientOrderId(),
            externalMarketSymbol(order.marketSymbol()),
            order.marketSymbol(),
            order.side(),
            order.orderType(),
            order.status(),
            order.timeInForce(),
            order.quantity(),
            order.limitPrice(),
            order.filledQuantity(),
            remaining,
            summary.averagePrice(),
            summary.lastExecutionAt(),
            order.createdAt(),
            order.updatedAt()
        );
    }

    private OrderPreviewResponse mapPreview(OrderPreviewPort.OrderPreview preview) {
        return new OrderPreviewResponse(
            externalMarketSymbol(preview.marketSymbol()),
            preview.marketSymbol(),
            preview.baseAsset(),
            preview.quoteAsset(),
            preview.side(),
            preview.orderType(),
            preview.timeInForce(),
            preview.quantity(),
            preview.limitPrice(),
            preview.notional(),
            preview.estimatedFee(),
            preview.feeAsset(),
            preview.feeRate(),
            preview.reserveAsset(),
            preview.reserveAmount(),
            preview.minOrderQuantity(),
            preview.minNotional(),
            preview.priceScale(),
            preview.quantityScale(),
            preview.supportedOrderTypes()
        );
    }

    private static String internalMarketSymbol(String symbol) {
        String value = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT).replace("-", "");
        return Optional.of(value)
            .filter(item -> item.endsWith("USDT") && item.length() > 4)
            .map(item -> item.substring(0, item.length() - 4) + "-USDT")
            .orElseGet(() -> symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT));
    }

    private static String externalMarketSymbol(String symbol) {
        return symbol == null ? "" : symbol.replace("-", "").toUpperCase(Locale.ROOT);
    }

    public record PlaceOrderRequest(
        @NotBlank @Size(max = 120) String clientOrderId,
        @NotBlank @Size(max = 80) String market,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @NotNull TimeInForce timeInForce,
        @DecimalMin(value = "0.000000000000000001") BigDecimal quantity,
        @DecimalMin(value = "0.000000000000000001") BigDecimal price,
        @DecimalMin(value = "0.000000000000000001") BigDecimal stopPrice
    ) {}

    public record PreviewOrderRequest(
        @NotBlank @Size(max = 80) String market,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @NotNull TimeInForce timeInForce,
        @DecimalMin(value = "0.000000000000000001") BigDecimal quantity,
        @DecimalMin(value = "0.000000000000000001") BigDecimal price
    ) {}

    public record OrderResponse(UUID orderId) {}
    public record OrderViewResponse(
        UUID id,
        UUID userId,
        String clientOrderId,
        String marketSymbol,
        String internalMarketSymbol,
        OrderSide side,
        OrderType orderType,
        OrderStatus status,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal averageExecutionPrice,
        Instant lastExecutionAt,
        Instant createdAt,
        Instant updatedAt
    ) {}
    public record OrderPreviewResponse(
        String marketSymbol,
        String internalMarketSymbol,
        String baseAsset,
        String quoteAsset,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal notional,
        BigDecimal estimatedFee,
        String feeAsset,
        BigDecimal feeRate,
        String reserveAsset,
        BigDecimal reserveAmount,
        BigDecimal minOrderQuantity,
        BigDecimal minNotional,
        int priceScale,
        int quantityScale,
        List<OrderType> supportedOrderTypes
    ) {}
    public record TradeHistoryResponse(String executionId, String market, String side, BigDecimal price, BigDecimal quantity, BigDecimal fee, Instant time) {}
}
