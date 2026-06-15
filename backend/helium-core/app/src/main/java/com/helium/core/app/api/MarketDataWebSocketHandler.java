package com.helium.core.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, Set<WebSocketSession>> sessionsByTopic = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public MarketDataWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionsByTopic.computeIfAbsent(topic(session), ignored -> ConcurrentHashMap.newKeySet()).add(session);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new SocketEvent("connected", topic(session), Instant.now(), null))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionsByTopic.values().forEach(sessions -> sessions.remove(session));
    }

    public void broadcast(String topic, String eventType, Object payload) {
        Set<WebSocketSession> sessions = sessionsByTopic.getOrDefault(topic, Set.of());
        if (sessions.isEmpty()) {
            return;
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(new SocketEvent(eventType, topic, Instant.now(), payload));
        } catch (IOException exception) {
            throw new IllegalStateException("websocket payload serialization failed", exception);
        }
        sessions.forEach(session -> send(session, body));
    }

    private void send(WebSocketSession session, String body) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(body));
            }
        } catch (IOException ignored) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException closeIgnored) {
                // session is already gone
            }
        }
    }

    private String topic(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return "unknown";
        }
        return uri.getPath().replace("/ws/markets/", "").toUpperCase();
    }

    private record SocketEvent(String type, String topic, Instant time, Object payload) {}
}
