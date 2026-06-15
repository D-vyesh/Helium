package com.helium.core.app.api;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisInfrastructureService {
    private final Optional<StringRedisTemplate> redisTemplate;

    public RedisInfrastructureService(ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.redisTemplate = Optional.ofNullable(redisTemplate.getIfAvailable());
    }

    public void cacheMarketData(String key, String payload, Duration ttl) {
        redisTemplate.ifPresent(template -> template.opsForValue().set("helium:market-data:" + key, payload, ttl));
    }

    public Optional<String> cachedMarketData(String key) {
        return redisTemplate.map(template -> template.opsForValue().get("helium:market-data:" + key));
    }

    public void publishWebSocketFanout(String topic, String payload) {
        redisTemplate.ifPresent(template -> template.convertAndSend("helium:websocket:" + topic, payload));
    }

    public boolean enabled() {
        return redisTemplate.isPresent();
    }
}
