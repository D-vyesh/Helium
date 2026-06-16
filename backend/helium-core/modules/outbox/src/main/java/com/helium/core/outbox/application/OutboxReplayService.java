package com.helium.core.outbox.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational service for replaying dead-lettered outbox events and querying outbox status.
 */
@Service
public class OutboxReplayService {
    private static final Logger log = LoggerFactory.getLogger(OutboxReplayService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OutboxReplayService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public boolean replayDeadLetter(UUID outboxEventId) {
        int updated = jdbcTemplate.update("""
            update outbox_events
               set status = 'PENDING', attempts = 0, next_attempt_at = ?, last_error = null, updated_at = ?
             where id = ?
               and status = 'DEAD_LETTER'
            """, Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)), outboxEventId);
        if (updated > 0) {
            jdbcTemplate.update("""
                update outbox_dead_letters
                   set replayed_at = ?, replay_count = replay_count + 1
                 where outbox_event_id = ?
                """, Timestamp.from(Instant.now(clock)), outboxEventId);
            log.info("Replayed dead-lettered outbox event {}", outboxEventId);
            return true;
        }
        return false;
    }

    @Transactional
    public int replayAllDeadLetters() {
        Instant now = Instant.now(clock);
        List<UUID> ids = jdbcTemplate.queryForList("""
            select id from outbox_events where status = 'DEAD_LETTER'
            order by created_at limit 500
            """, UUID.class);
        int replayed = 0;
        for (UUID id : ids) {
            if (replayDeadLetter(id)) {
                replayed++;
            }
        }
        log.info("Replayed {} dead-lettered outbox events", replayed);
        return replayed;
    }

    public OutboxStats stats() {
        return jdbcTemplate.queryForObject("""
            select
                count(*) filter (where status = 'PENDING') as pending,
                count(*) filter (where status = 'FAILED') as failed,
                count(*) filter (where status = 'DEAD_LETTER') as dead_letter,
                count(*) filter (where status = 'PUBLISHED') as published
            from outbox_events
            """, (rs, rowNum) -> new OutboxStats(
            rs.getInt("pending"),
            rs.getInt("failed"),
            rs.getInt("dead_letter"),
            rs.getInt("published")
        ));
    }

    public List<DeadLetterView> deadLetters(int limit, int offset) {
        return jdbcTemplate.query("""
            select dl.id, dl.outbox_event_id, dl.event_type, dl.error, dl.created_at,
                   dl.replayed_at, dl.replay_count, oe.aggregate_type, oe.aggregate_id
              from outbox_dead_letters dl
              join outbox_events oe on oe.id = dl.outbox_event_id
             order by dl.created_at desc
             limit ? offset ?
            """, (rs, rowNum) -> new DeadLetterView(
            rs.getObject("id", UUID.class),
            rs.getObject("outbox_event_id", UUID.class),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("event_type"),
            rs.getString("error"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("replayed_at") != null ? rs.getTimestamp("replayed_at").toInstant() : null,
            rs.getInt("replay_count")
        ), limit, offset);
    }

    public record OutboxStats(int pending, int failed, int deadLetter, int published) {}

    public record DeadLetterView(
        UUID id, UUID outboxEventId, String aggregateType, String aggregateId,
        String eventType, String error, Instant createdAt, Instant replayedAt, int replayCount
    ) {}
}
