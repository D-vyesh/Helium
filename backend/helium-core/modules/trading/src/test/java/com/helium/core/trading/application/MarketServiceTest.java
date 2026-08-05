package com.helium.core.trading.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.TradingSecurityContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketServiceTest {

    @Test
    void mapsConfiguredMarketPrecisionRatherThanDecimalValueScale() {
        MarketRepository repository = mock(MarketRepository.class);
        Market market = Market.register(
            "BTC-USDT",
            "BTC",
            "USDT",
            8,
            6,
            new BigDecimal("0.010000"),
            new BigDecimal("10.00"),
            true,
            Instant.parse("2026-08-04T00:00:00Z")
        );
        when(repository.findById("BTC-USDT")).thenReturn(Optional.of(market));

        MarketService service = new MarketService(
            repository,
            mock(AuthorizationPort.class),
            mock(TradingSecurityContext.class),
            Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC)
        );

        MarketQueryPort.MarketView view = service.getMarket("BTC-USDT").orElseThrow();

        assertThat(view.priceScale()).isEqualTo(8);
        assertThat(view.quantityScale()).isEqualTo(6);
    }
}
