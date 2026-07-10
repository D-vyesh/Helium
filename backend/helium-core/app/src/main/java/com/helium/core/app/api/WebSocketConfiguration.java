package com.helium.core.app.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfiguration.class);

    private final MarketDataWebSocketHandler handler;
    private final TradingWebSocketHandler tradingHandler;
    private final NotificationWebSocketHandler notificationHandler;

    public WebSocketConfiguration(
        MarketDataWebSocketHandler handler,
        TradingWebSocketHandler tradingHandler,
        NotificationWebSocketHandler notificationHandler
    ) {
        this.handler = handler;
        this.tradingHandler = tradingHandler;
        this.notificationHandler = notificationHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                handler,
                "/ws/markets/{symbol}/trades",
                "/ws/markets/{symbol}/orderbook",
                "/ws/markets/{symbol}/ticker",
                "/ws/markets/{symbol}/candles"
            )
            .setAllowedOriginPatterns("http://localhost:*", "https://*.helium.exchange");
        registry.addHandler(tradingHandler, "/ws/trading/orders")
            .setAllowedOriginPatterns("http://localhost:*", "https://*.helium.exchange");
        registry.addHandler(notificationHandler, "/ws/notifications")
            .setAllowedOriginPatterns("http://localhost:*", "https://*.helium.exchange");
        log.info("Public market-data and private account WebSocket handlers registered");
    }
}
