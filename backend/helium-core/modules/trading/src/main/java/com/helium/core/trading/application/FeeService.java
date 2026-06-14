package com.helium.core.trading.application;

import com.helium.core.trading.domain.FeeSchedule;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.FeeScheduleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
class FeeService {
    private final FeeScheduleRepository repository;

    FeeService(FeeScheduleRepository repository) {
        this.repository = repository;
    }

    public BigDecimal estimateFee(String marketSymbol, OrderSide side, BigDecimal quantity, BigDecimal price) {
        return estimate(marketSymbol, side, quantity, price).amount();
    }

    FeeEstimate estimate(String marketSymbol, OrderSide side, BigDecimal quantity, BigDecimal price) {
        String normalizedSymbol = Market.normalizeSymbol(marketSymbol);
        String[] assets = normalizedSymbol.split("-");
        return estimate(Market.register(
            normalizedSymbol,
            assets[0],
            assets[1],
            18,
            18,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            java.time.Instant.EPOCH
        ), side, quantity, price);
    }

    FeeEstimate estimate(Market market, OrderSide side, BigDecimal quantity, BigDecimal price) {
        FeeSchedule schedule = repository.findByMarketSymbol(market.symbol())
            .orElseThrow(() -> new TradingValidationException("no fee schedule for market"));

        if (!schedule.enabled()) {
            throw new TradingValidationException("fee schedule disabled");
        }

        BigDecimal rate = schedule.takerFeeRate(); // Estimate with taker fee for worst case
        BigDecimal notional = quantity.multiply(price);
        if (side == OrderSide.SELL && schedule.sellFeeAsset() == com.helium.core.trading.domain.FeeAssetType.BASE) {
            return new FeeEstimate(
                quantity.multiply(rate).setScale(18, RoundingMode.HALF_UP).stripTrailingZeros(),
                com.helium.core.trading.domain.FeeAssetType.BASE,
                market.baseAsset(),
                rate,
                schedule.id().toString()
            );
        }
        return new FeeEstimate(
            notional.multiply(rate).setScale(18, RoundingMode.HALF_UP).stripTrailingZeros(),
            com.helium.core.trading.domain.FeeAssetType.QUOTE,
            market.quoteAsset(),
            rate,
            schedule.id().toString()
        );
    }
}
