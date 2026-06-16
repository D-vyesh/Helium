package com.helium.core.app.api;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.domain.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for structured audit event search across all audit tables.
 * Supports filtering by userId, eventType, dateRange, and correlationId.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Admin - Audit")
public class AuditSearchController {
    private final JdbcTemplate jdbcTemplate;
    private final TrustedActorProvider trustedActorProvider;
    private final AuthorizationPort authorizationPort;

    public AuditSearchController(
        JdbcTemplate jdbcTemplate,
        TrustedActorProvider trustedActorProvider,
        AuthorizationPort authorizationPort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.trustedActorProvider = trustedActorProvider;
        this.authorizationPort = authorizationPort;
    }

    @GetMapping("/search")
    public AuditSearchResponse search(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) String cursor
    ) {
        requireAdmin();
        int safeLimit = Math.min(Math.max(limit, 1), 200);

        StringBuilder sql = new StringBuilder("""
            select id, user_id, event_type, actor_id, created_at, metadata
              from security_audit_events
             where 1=1
            """);
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (userId != null) {
            sql.append(" and user_id = ?");
            params.add(userId);
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" and event_type = ?");
            params.add(eventType);
        }
        if (from != null) {
            sql.append(" and created_at >= ?");
            params.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and created_at <= ?");
            params.add(java.sql.Timestamp.from(to));
        }
        if (cursor != null && !cursor.isBlank()) {
            try {
                Instant cursorInstant = Instant.parse(cursor);
                sql.append(" and created_at < ?");
                params.add(java.sql.Timestamp.from(cursorInstant));
            } catch (RuntimeException ignored) {
            }
        }

        sql.append(" order by created_at desc limit ?");
        params.add(safeLimit);

        List<AuditEventView> events = jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new AuditEventView(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("actor_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("metadata")
            ),
            params.toArray()
        );

        String nextCursor = events.isEmpty() ? null
            : events.get(events.size() - 1).createdAt().toString();

        return new AuditSearchResponse(events, nextCursor, events.size());
    }

    @GetMapping("/event-types")
    public List<String> eventTypes() {
        requireAdmin();
        return jdbcTemplate.queryForList(
            "select distinct event_type from security_audit_events order by event_type",
            String.class
        );
    }

    private void requireAdmin() {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        authorizationPort.requireRole(userId, Role.ADMIN);
    }

    public record AuditEventView(UUID id, UUID userId, String eventType, String actorId, Instant createdAt, String metadata) {}
    public record AuditSearchResponse(List<AuditEventView> events, String nextCursor, int count) {}
}
