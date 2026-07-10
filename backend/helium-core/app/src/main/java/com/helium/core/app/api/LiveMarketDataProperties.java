package com.helium.core.app.api;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "helium.market-data.live")
public record LiveMarketDataProperties(
    boolean enabled,
    String restBaseUrl,
    String websocketBaseUrl,
    List<String> symbols,
    Duration restTimeout,
    Duration reconnectInitialDelay,
    Duration reconnectMaxDelay,
    Duration cacheTtl,
    int orderBookLimit,
    int tradeLimit,
    int candleLimit
) {
    public LiveMarketDataProperties {
        if (restBaseUrl == null || restBaseUrl.isBlank()) {
            restBaseUrl = "https://api.binance.com";
        }
        if (websocketBaseUrl == null || websocketBaseUrl.isBlank()) {
            websocketBaseUrl = "wss://stream.binance.com:9443";
        }
        if (symbols == null || symbols.isEmpty()) {
            symbols = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT");
        }
        symbols = symbols.stream().map(symbol -> symbol.trim().toUpperCase()).filter(symbol -> !symbol.isBlank()).toList();
        restTimeout = restTimeout == null ? Duration.ofSeconds(5) : restTimeout;
        reconnectInitialDelay = reconnectInitialDelay == null ? Duration.ofSeconds(1) : reconnectInitialDelay;
        reconnectMaxDelay = reconnectMaxDelay == null ? Duration.ofSeconds(30) : reconnectMaxDelay;
        cacheTtl = cacheTtl == null ? Duration.ofSeconds(20) : cacheTtl;
        orderBookLimit = orderBookLimit <= 0 ? 100 : Math.min(orderBookLimit, 5000);
        tradeLimit = tradeLimit <= 0 ? 80 : Math.min(tradeLimit, 1000);
        candleLimit = candleLimit <= 0 ? 240 : Math.min(candleLimit, 1000);
    }
}
