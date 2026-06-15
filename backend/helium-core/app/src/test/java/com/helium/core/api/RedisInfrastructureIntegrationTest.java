package com.helium.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.helium.core.app.HeliumCoreApplication;
import com.helium.core.app.api.RedisInfrastructureService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = HeliumCoreApplication.class)
@Testcontainers
class RedisInfrastructureIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Autowired
    private RedisInfrastructureService redis;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("helium.outbox.enabled", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Test
    void cachesMarketDataInRedis() {
        redis.cacheMarketData("ticker:BTC-USD", "{\"last\":\"100.00\"}", Duration.ofMinutes(1));

        assertThat(redis.enabled()).isTrue();
        assertThat(redis.cachedMarketData("ticker:BTC-USD")).contains("{\"last\":\"100.00\"}");
    }
}
