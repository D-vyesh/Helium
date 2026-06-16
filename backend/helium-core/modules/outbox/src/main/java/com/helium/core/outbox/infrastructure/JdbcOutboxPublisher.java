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

    @Override
    @Transactional
    public boolean publishWithDeduplication(String deduplicationKey, String aggregateType, String aggregateId, String eventType, String payload) {
        Instant now = Instant.now(clock);
        int inserted = jdbcTemplate.update("""
            insert into outbox_events
                (id, aggregate_type, aggregate_id, event_type, payload, status, attempts, next_attempt_at, deduplication_key, created_at, updated_at)
            values (?, ?, ?, ?, ?::jsonb, 'PENDING', 0, ?, ?, ?, ?)
            on conflict (deduplication_key) where deduplication_key is not null do nothing
            """, UUID.randomUUID(), aggregateType, aggregateId, eventType, payload, Timestamp.from(now), deduplicationKey, Timestamp.from(now), Timestamp.from(now));
        return inserted > 0;
    }
}
