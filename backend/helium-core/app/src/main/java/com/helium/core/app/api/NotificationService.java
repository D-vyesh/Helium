package com.helium.core.app.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationWebSocketHandler webSocketHandler;
    private final Clock clock;

    public NotificationService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        NotificationWebSocketHandler webSocketHandler,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.clock = clock;
    }

    @Transactional
    public NotificationView create(
        UUID userId,
        String category,
        String eventType,
        String title,
        String message,
        Map<String, Object> payload
    ) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into exchange_notifications (id, user_id, category, event_type, title, message, payload, created_at)
            values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """,
            id,
            userId,
            category,
            eventType,
            title,
            message,
            json(payload),
            timestamp(now)
        );
        NotificationView notification = new NotificationView(id, userId, category, eventType, title, message, payload, false, now, null);
        webSocketHandler.broadcast(userId, "notification", notification);
        webSocketHandler.broadcast(userId, "unread-count", new UnreadCount(unreadCount(userId)));
        return notification;
    }

    @Transactional(readOnly = true)
    public java.util.List<NotificationView> list(UUID userId, Instant before, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        if (before == null) {
            return jdbcTemplate.query(
                """
                select id, user_id, category, event_type, title, message, payload::text, created_at, read_at
                from exchange_notifications
                where user_id = ? and deleted_at is null
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> notification(rs),
                userId,
                size
            );
        }
        return jdbcTemplate.query(
            """
            select id, user_id, category, event_type, title, message, payload::text, created_at, read_at
            from exchange_notifications
            where user_id = ? and deleted_at is null and created_at < ?
            order by created_at desc
            limit ?
            """,
            (rs, rowNum) -> notification(rs),
            userId,
            timestamp(before),
            size
        );
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from exchange_notifications where user_id = ? and read_at is null and deleted_at is null",
            Long.class,
            userId
        );
        return count == null ? 0L : count;
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        jdbcTemplate.update(
            """
            update exchange_notifications
            set read_at = coalesce(read_at, ?)
            where id = ? and user_id = ? and deleted_at is null
            """,
            timestamp(clock.instant()),
            notificationId,
            userId
        );
        webSocketHandler.broadcast(userId, "unread-count", new UnreadCount(unreadCount(userId)));
    }

    @Transactional
    public void markAllRead(UUID userId) {
        jdbcTemplate.update(
            """
            update exchange_notifications
            set read_at = coalesce(read_at, ?)
            where user_id = ? and read_at is null and deleted_at is null
            """,
            timestamp(clock.instant()),
            userId
        );
        webSocketHandler.broadcast(userId, "unread-count", new UnreadCount(unreadCount(userId)));
    }

    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        jdbcTemplate.update(
            """
            update exchange_notifications
            set deleted_at = coalesce(deleted_at, ?)
            where id = ? and user_id = ?
            """,
            timestamp(clock.instant()),
            notificationId,
            userId
        );
        webSocketHandler.broadcast(userId, "unread-count", new UnreadCount(unreadCount(userId)));
    }

    private NotificationView notification(ResultSet rs) throws SQLException {
        Instant readAt = rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant();
        return new NotificationView(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("category"),
            rs.getString("event_type"),
            rs.getString("title"),
            rs.getString("message"),
            map(rs.getString("payload")),
            readAt != null,
            rs.getTimestamp("created_at").toInstant(),
            readAt
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("notification payload is not serializable", exception);
        }
    }

    private Map<String, Object> map(String value) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? "{}" : value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("notification payload is invalid", exception);
        }
    }

    public record NotificationView(
        UUID id,
        UUID userId,
        String category,
        String eventType,
        String title,
        String message,
        Map<String, Object> payload,
        boolean read,
        Instant createdAt,
        Instant readAt
    ) {}

    public record UnreadCount(long unread) {}
}
