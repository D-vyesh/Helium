package com.helium.core.outbox.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(MeterRegistry.class)
public class OutboxMetrics {
    public OutboxMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        Gauge.builder("outbox_events_pending", jdbcTemplate, template -> count(template, "PENDING"))
            .description("Number of pending outbox events")
            .register(registry);
        Gauge.builder("outbox_events_dead_letter", jdbcTemplate, template -> count(template, "DEAD_LETTER"))
            .description("Number of dead-lettered outbox events")
            .register(registry);
    }

    private static double count(JdbcTemplate jdbcTemplate, String status) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from outbox_events where status = ?",
            Integer.class,
            status
        );
        return count == null ? 0.0 : count.doubleValue();
    }
}
