package com.helium.core.trading.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.helium.core.trading.domain.FeeAssetType;
import com.helium.core.trading.domain.FeeSchedule;
import com.helium.core.trading.domain.OrderSide;
import com.helium.core.trading.infrastructure.FeeScheduleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FeeServiceTest {
    private final FeeScheduleRepository repository = Mockito.mock(FeeScheduleRepository.class);
    private final FeeService feeService = new FeeService(repository);

    @Test
    void estimatesBaseFeeForSellWhenScheduleChargesBase() {
        when(repository.findByMarketSymbol("BTC-USD")).thenReturn(Optional.of(schedule(FeeAssetType.BASE)));

        FeeEstimate estimate = feeService.estimate("BTC-USD", OrderSide.SELL, new BigDecimal("2.0"), new BigDecimal("100.00"));

        assertThat(estimate.assetType()).isEqualTo(FeeAssetType.BASE);
        assertThat(estimate.amount()).isEqualByComparingTo("0.02");
    }

    @Test
    void estimatesQuoteFeeForBuyAndQuoteChargedSell() {
        when(repository.findByMarketSymbol("BTC-USD")).thenReturn(Optional.of(schedule(FeeAssetType.QUOTE)));

        FeeEstimate buy = feeService.estimate("BTC-USD", OrderSide.BUY, new BigDecimal("2.0"), new BigDecimal("100.00"));
        FeeEstimate sell = feeService.estimate("BTC-USD", OrderSide.SELL, new BigDecimal("2.0"), new BigDecimal("100.00"));

        assertThat(buy.assetType()).isEqualTo(FeeAssetType.QUOTE);
        assertThat(buy.amount()).isEqualByComparingTo("2.00");
        assertThat(sell.assetType()).isEqualTo(FeeAssetType.QUOTE);
        assertThat(sell.amount()).isEqualByComparingTo("2.00");
    }

    private static FeeSchedule schedule(FeeAssetType sellFeeAsset) {
        return FeeSchedule.configure(
            "BTC-USD",
            BigDecimal.ZERO,
            new BigDecimal("0.01"),
            sellFeeAsset,
            true,
            Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
