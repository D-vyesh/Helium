package com.helium.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.outbox.application.OutboxEventHandler;
import com.helium.core.outbox.application.OutboxMessage;
import com.helium.core.outbox.application.OutboxPublisher;
import com.helium.core.outbox.infrastructure.OutboxProcessor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {HeliumCoreApplication.class, OutboxPostgresIntegrationTest.Config.class})
@Testcontainers
class OutboxPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturingHandler handler;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("helium.outbox.enabled", () -> "true");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @BeforeEach
    void clear() {
        jdbcTemplate.execute("truncate table outbox_dead_letters, outbox_events cascade");
        handler.count.set(0);
    }

    @Test
    void persistsAndProcessesOutboxEvents() {
        publisher.publish("matching", "BTC-USD", "MatchingExecutionCreated", "{\"ok\":true}");

        processor.poll();

        assertThat(handler.count.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from outbox_events", String.class)).isEqualTo("PUBLISHED");
    }

    @TestConfiguration
    static class Config {
        @Bean
        CapturingHandler capturingHandler() {
            return new CapturingHandler();
        }
    }

    static class CapturingHandler implements OutboxEventHandler {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public boolean supports(String eventType) {
            return "MatchingExecutionCreated".equals(eventType);
        }

        @Override
        public void handle(OutboxMessage message) {
            count.incrementAndGet();
        }
    }
}
