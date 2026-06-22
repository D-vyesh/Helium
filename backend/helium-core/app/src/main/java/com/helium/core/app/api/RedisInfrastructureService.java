package com.helium.core.app.api;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisInfrastructureService {
    private final Optional<StringRedisTemplate> redisTemplate;

    public RedisInfrastructureService(ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.redisTemplate = Optional.ofNullable(redisTemplate.getIfAvailable());
    }

    public void cacheMarketData(String key, String payload, Duration ttl) {
        try {
            redisTemplate.ifPresent(template -> template.opsForValue().set("helium:market-data:" + key, payload, ttl));
        } catch (DataAccessException exception) {
            // Redis is optional for non-Redis integration tests.
        }
    }

    public Optional<String> cachedMarketData(String key) {
        try {
            return redisTemplate.map(template -> template.opsForValue().get("helium:market-data:" + key));
        } catch (DataAccessException exception) {
            return Optional.empty();
        }
    }

    public void publishWebSocketFanout(String topic, String payload) {
        try {
            redisTemplate.ifPresent(template -> template.convertAndSend("helium:websocket:" + topic, payload));
        } catch (DataAccessException exception) {
            // Local websocket delivery continues without Redis fanout.
        }
    }

    public boolean enabled() {
        return redisTemplate.isPresent();
    }
}
