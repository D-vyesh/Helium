package com.helium.core.app.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.authuser.application.SecurityContextData;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountActivityService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountActivityService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(
        UUID userId,
        String category,
        String eventType,
        String summary,
        String status,
        String actorId,
        SecurityContextData securityContext,
        Map<String, Object> metadata
    ) {
        jdbcTemplate.update(
            """
            insert into exchange_activity_events (
                id, user_id, category, event_type, summary, status, actor_id,
                ip_address, user_agent, device_info, metadata, created_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """,
            UUID.randomUUID(),
            userId,
            category,
            eventType,
            summary,
            status,
            actorId,
            securityContext == null ? null : securityContext.ipAddress(),
            securityContext == null ? null : securityContext.userAgent(),
            securityContext == null ? null : securityContext.deviceInfo(),
            json(metadata),
            clock.instant()
        );
    }

    private String json(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("activity metadata is not serializable", exception);
        }
    }
}
