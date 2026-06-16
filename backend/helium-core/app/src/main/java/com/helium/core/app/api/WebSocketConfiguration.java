package com.helium.core.app.api;

import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.application.SessionView;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfiguration.class);

    private final MarketDataWebSocketHandler handler;
    private final SessionPort sessionPort;

    public WebSocketConfiguration(MarketDataWebSocketHandler handler, SessionPort sessionPort) {
        this.handler = handler;
        this.sessionPort = sessionPort;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/markets/{symbol}/trades", "/ws/markets/{symbol}/orderbook", "/ws/markets/{symbol}/ticker")
            .addInterceptors(new AuthenticatedHandshakeInterceptor(sessionPort))
            .setAllowedOriginPatterns("http://localhost:*", "https://*.helium.exchange");
    }

    /**
     * Enforces authentication on WebSocket handshake.
     * Rejects connections without a valid session token.
     */
    private static class AuthenticatedHandshakeInterceptor implements HandshakeInterceptor {
        private final SessionPort sessionPort;

        private AuthenticatedHandshakeInterceptor(SessionPort sessionPort) {
            this.sessionPort = sessionPort;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            if (query == null || !query.contains("token=")) {
                log.debug("WebSocket handshake rejected: no token provided");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            String token = extractToken(query);
            if (token == null || token.isBlank()) {
                log.debug("WebSocket handshake rejected: empty token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            Optional<SessionView> session = sessionPort.validate(token);
            if (session.isEmpty()) {
                log.debug("WebSocket handshake rejected: invalid session");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            SessionView sessionView = session.get();
            attributes.put("userId", sessionView.userId());
            attributes.put("sessionId", sessionView.sessionId());
            attributes.put("roles", sessionView.roles());
            log.debug("WebSocket handshake authenticated: userId={}", sessionView.userId());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractToken(String query) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring("token=".length());
                }
            }
            return null;
        }
    }
}
