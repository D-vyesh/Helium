package com.helium.core.app.api;

import com.helium.core.authuser.application.SessionPort;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final MarketDataWebSocketHandler handler;
    private final SessionPort sessionPort;

    public WebSocketConfiguration(MarketDataWebSocketHandler handler, SessionPort sessionPort) {
        this.handler = handler;
        this.sessionPort = sessionPort;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/markets/{symbol}/trades", "/ws/markets/{symbol}/orderbook", "/ws/markets/{symbol}/ticker")
            .addInterceptors(new SessionHandshakeInterceptor(sessionPort))
            .setAllowedOriginPatterns("http://localhost:*", "https://*.helium.exchange");
    }

    private static class SessionHandshakeInterceptor implements HandshakeInterceptor {
        private final SessionPort sessionPort;

        private SessionHandshakeInterceptor(SessionPort sessionPort) {
            this.sessionPort = sessionPort;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            if (query != null && query.startsWith("token=")) {
                sessionPort.validate(query.substring("token=".length())).ifPresent(session -> attributes.put("userId", session.userId()));
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
