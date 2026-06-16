package com.helium.core.outbox.infrastructure;

import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "helium.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final int MAX_ATTEMPTS = 8;
    private static final long BASE_DELAY_SECONDS = 2;
    private static final long MAX_DELAY_SECONDS = 300;
    private static final double JITTER_FACTOR = 0.2;

    private final JdbcTemplate jdbcTemplate;
    private final List<OutboxEventHandler> handlers;
    private final Clock clock;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;
    private final Timer processingTimer;

    public OutboxProcessor(
        JdbcTemplate jdbcTemplate,
        ObjectProvider<OutboxEventHandler> handlers,
        Clock clock,
        ObjectProvider<MeterRegistry> registryProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.handlers = handlers.orderedStream().toList();
        this.clock = clock;

        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            this.processedCounter = Counter.builder("outbox_events_processed_total")
                .description("Total outbox events successfully processed")
                .register(registry);
            this.failedCounter = Counter.builder("outbox_events_failed_total")
                .description("Total outbox events that failed processing")
                .register(registry);
            this.deadLetterCounter = Counter.builder("outbox_events_dead_letter_total")
                .description("Total outbox events moved to dead letter")
                .register(registry);
            this.processingTimer = Timer.builder("outbox_processing_duration_seconds")
                .description("Time spent processing outbox events")
                .register(registry);
        } else {
            this.processedCounter = null;
            this.failedCounter = null;
            this.deadLetterCounter = null;
            this.processingTimer = null;
        }
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
        Timer.Sample sample = processingTimer != null ? Timer.start() : null;
        try {
            handlers.stream()
                .filter(handler -> handler.supports(message.eventType()))
                .forEach(handler -> handler.handle(message));
            markPublished(message.id());
            if (processedCounter != null) {
                processedCounter.increment();
            }
        } catch (RuntimeException exception) {
            log.warn("Outbox event {} processing failed (attempt {}): {}",
                message.id(), message.attempts() + 1, exception.getMessage());
            markFailed(message, exception);
            if (failedCounter != null) {
                failedCounter.increment();
            }
        } finally {
            if (sample != null && processingTimer != null) {
                sample.stop(processingTimer);
            }
        }
    }

    private void markPublished(UUID id) {
        Instant now = Instant.now(clock);
        jdbcTemplate.update("""
            update outbox_events
               set status = 'PUBLISHED', published_at = ?, updated_at = ?
             where id = ?
            """, Timestamp.from(now), Timestamp.from(now), id);
    }

    private void markFailed(OutboxMessage message, RuntimeException exception) {
        int attempts = message.attempts() + 1;
        String status = attempts >= MAX_ATTEMPTS ? "DEAD_LETTER" : "FAILED";
        Instant nextAttempt = calculateNextAttempt(attempts);
        Instant now = Instant.now(clock);

        jdbcTemplate.update("""
            update outbox_events
               set status = ?, attempts = ?, last_error = ?, next_attempt_at = ?, updated_at = ?
             where id = ?
            """, status, attempts, exception.getMessage(), Timestamp.from(nextAttempt), Timestamp.from(now), message.id());

        if ("DEAD_LETTER".equals(status)) {
            jdbcTemplate.update("""
                insert into outbox_dead_letters (id, outbox_event_id, event_type, payload, error, created_at)
                values (?, ?, ?, ?::jsonb, ?, ?)
                on conflict (outbox_event_id) do nothing
                """, UUID.randomUUID(), message.id(), message.eventType(), message.payload(), exception.getMessage(), Timestamp.from(now));
            if (deadLetterCounter != null) {
                deadLetterCounter.increment();
            }
            log.error("Outbox event {} moved to dead letter after {} attempts", message.id(), attempts);
        }
    }

    /**
     * Exponential backoff with jitter: base * 2^attempt, capped at MAX_DELAY_SECONDS, ±20% jitter.
     */
    private Instant calculateNextAttempt(int attempts) {
        long delay = (long) Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * Math.pow(2, attempts));
        long jitter = (long) (delay * JITTER_FACTOR * (2 * ThreadLocalRandom.current().nextDouble() - 1));
        return Instant.now(clock).plusSeconds(Math.max(1, delay + jitter));
    }
}
