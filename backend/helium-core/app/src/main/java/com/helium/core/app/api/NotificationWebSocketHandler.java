package com.helium.core.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Optional<UUID> userId = userId(session);
        if (userId.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("userId", userId.get());
        sessionsByUser.computeIfAbsent(userId.get(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
        send(session, event("connected", null));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof UUID uuid) {
            sessionsByUser.getOrDefault(uuid, Set.of()).remove(session);
        }
    }

    public void broadcast(UUID userId, String type, Object payload) {
        String body = event(type, payload);
        sessionsByUser.getOrDefault(userId, Set.of()).forEach(session -> send(session, body));
    }

    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        String body = event("heartbeat", null);
        sessionsByUser.values().forEach(sessions -> sessions.forEach(session -> send(session, body)));
    }

    private Optional<UUID> userId(WebSocketSession session) {
        return token(session)
            .flatMap(jwtTokenService::validate)
            .map(JwtTokenService.AccessTokenClaims::userId);
    }

    private Optional<String> token(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return Optional.empty();
        }
        for (String part : uri.getQuery().split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0 && "token".equals(part.substring(0, equals))) {
                return Optional.of(part.substring(equals + 1)).filter(value -> !value.isBlank());
            }
        }
        return Optional.empty();
    }

    private String event(String type, Object payload) {
        try {
            return objectMapper.writeValueAsString(new SocketEvent(type, Instant.now(), payload));
        } catch (IOException exception) {
            throw new IllegalStateException("notification websocket payload serialization failed", exception);
        }
    }

    private void send(WebSocketSession session, String body) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(body));
                    }
                }
            }
        } catch (IOException ignored) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException closeIgnored) {
                // session is already gone
            }
        }
    }

    private record SocketEvent(String type, Instant time, Object payload) {}
}
