package com.helium.core.trading.application;

import com.helium.core.trading.domain.FeeAssetType;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.OrderType;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.MarketRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderPreviewService implements OrderPreviewPort {
    private final MarketRepository marketRepository;
    private final FeeService feeService;

    OrderPreviewService(MarketRepository marketRepository, FeeService feeService) {
        this.marketRepository = marketRepository;
        this.feeService = feeService;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPreview preview(PreviewOrderCommand command) {
        Market market = marketRepository.findById(Market.normalizeSymbol(command.marketSymbol()))
            .orElseThrow(() -> new TradingValidationException("market not found"));
        BigDecimal quantity = Market.requirePositive(command.quantity(), "quantity");
        BigDecimal limitPrice = command.limitPrice() != null ? Market.requirePositive(command.limitPrice(), "limitPrice") : null;
        validate(command, market, quantity, limitPrice);
        BigDecimal effectivePrice = limitPrice != null ? limitPrice : BigDecimal.ONE;
        FeeEstimate fee = feeService.estimate(market, command.side(), quantity, effectivePrice);
        BigDecimal notional = limitPrice != null ? quantity.multiply(limitPrice).stripTrailingZeros() : BigDecimal.ZERO;
        String reserveAsset;
        BigDecimal reserveAmount;
        if (command.side() == OrderSide.BUY) {
            reserveAsset = market.quoteAsset();
            reserveAmount = notional.add(fee.amount()).stripTrailingZeros();
        } else {
            reserveAsset = market.baseAsset();
            reserveAmount = fee.assetType() == FeeAssetType.BASE
                ? quantity.add(fee.amount()).stripTrailingZeros()
                : quantity.stripTrailingZeros();
        }
        return new OrderPreview(
            market.symbol(),
            market.baseAsset(),
            market.quoteAsset(),
            command.side(),
            command.orderType(),
            command.timeInForce(),
            quantity,
            limitPrice,
            notional,
            fee.amount(),
            fee.assetCode(),
            fee.rate(),
            reserveAsset,
            reserveAmount,
            market.minOrderQuantity(),
            market.minNotional(),
            market.priceScale(),
            market.quantityScale(),
            List.of(OrderType.LIMIT, OrderType.MARKET, OrderType.STOP_LIMIT, OrderType.POST_ONLY)
        );
    }

    private void validate(PreviewOrderCommand command, Market market, BigDecimal quantity, BigDecimal limitPrice) {
        if (!market.enabled()) {
            throw new TradingValidationException("market is disabled");
        }
        if (limitPrice != null) {
            if (limitPrice.scale() > market.priceScale()) {
                throw new TradingValidationException("price increment is invalid for market");
            }
            BigDecimal notional = quantity.multiply(limitPrice).stripTrailingZeros();
            if (notional.compareTo(market.minNotional()) < 0) {
                throw new TradingValidationException("order notional is below market minimum");
            }
        }
        if (quantity.scale() > market.quantityScale()) {
            throw new TradingValidationException("quantity increment is invalid for market");
        }
        if (quantity.compareTo(market.minOrderQuantity()) < 0) {
            throw new TradingValidationException("order quantity is below market minimum");
        }
    }
}
