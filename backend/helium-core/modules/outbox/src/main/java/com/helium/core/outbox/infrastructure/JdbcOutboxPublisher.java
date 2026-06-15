package com.helium.core.outbox.infrastructure;

import com.helium.core.outbox.application.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcOutboxPublisher implements OutboxPublisher {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcOutboxPublisher(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publish(String aggregateType, String aggregateId, String eventType, String payload) {
        Instant now = Instant.now(clock);
        jdbcTemplate.update("""
            insert into outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, status, attempts, next_attempt_at, created_at, updated_at)
            values (?, ?, ?, ?, ?::jsonb, 'PENDING', 0, ?, ?, ?)
            """, UUID.randomUUID(), aggregateType, aggregateId, eventType, payload, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
}
