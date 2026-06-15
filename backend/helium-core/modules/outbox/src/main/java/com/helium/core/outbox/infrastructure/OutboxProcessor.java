package com.helium.core.outbox.infrastructure;

import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "helium.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxProcessor {
    private static final int MAX_ATTEMPTS = 8;
    private final JdbcTemplate jdbcTemplate;
    private final List<OutboxEventHandler> handlers;
    private final Clock clock;

    public OutboxProcessor(JdbcTemplate jdbcTemplate, ObjectProvider<OutboxEventHandler> handlers, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.handlers = handlers.orderedStream().toList();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${helium.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        List<OutboxMessage> messages = jdbcTemplate.query("""
            select id, aggregate_type, aggregate_id, event_type, payload::text, attempts, created_at
              from outbox_events
             where status in ('PENDING', 'FAILED')
               and next_attempt_at <= now()
             order by created_at
             limit 50
             for update skip locked
            """, (rs, rowNum) -> new OutboxMessage(
            rs.getObject("id", UUID.class),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("event_type"),
            rs.getString("payload"),
            rs.getInt("attempts"),
            rs.getTimestamp("created_at").toInstant()
        ));
        messages.forEach(this::process);
    }

    private void process(OutboxMessage message) {
        try {
            handlers.stream()
                .filter(handler -> handler.supports(message.eventType()))
                .forEach(handler -> handler.handle(message));
            markPublished(message.id());
        } catch (RuntimeException exception) {
            markFailed(message, exception);
        }
    }

    private void markPublished(UUID id) {
        jdbcTemplate.update("""
            update outbox_events
               set status = 'PUBLISHED', published_at = ?, updated_at = ?
             where id = ?
            """, Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)), id);
    }

    private void markFailed(OutboxMessage message, RuntimeException exception) {
        int attempts = message.attempts() + 1;
        String status = attempts >= MAX_ATTEMPTS ? "DEAD_LETTER" : "FAILED";
        Instant nextAttempt = Instant.now(clock).plusSeconds((long) Math.min(300, Math.pow(2, attempts)));
        jdbcTemplate.update("""
            update outbox_events
               set status = ?, attempts = ?, last_error = ?, next_attempt_at = ?, updated_at = ?
             where id = ?
            """, status, attempts, exception.getMessage(), Timestamp.from(nextAttempt), Timestamp.from(Instant.now(clock)), message.id());
        if ("DEAD_LETTER".equals(status)) {
            jdbcTemplate.update("""
                insert into outbox_dead_letters (id, outbox_event_id, event_type, payload, error, created_at)
                values (?, ?, ?, ?::jsonb, ?, ?)
                on conflict (outbox_event_id) do nothing
                """, UUID.randomUUID(), message.id(), message.eventType(), message.payload(), exception.getMessage(), Timestamp.from(Instant.now(clock)));
        }
    }
}
