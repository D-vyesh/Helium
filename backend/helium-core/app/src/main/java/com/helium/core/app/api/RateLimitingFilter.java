package com.helium.core.app.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS_PER_MINUTE = 300;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Optional<StringRedisTemplate> redisTemplate;

    public RateLimitingFilter(Clock clock, ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.clock = clock;
        this.redisTemplate = Optional.ofNullable(redisTemplate.getIfAvailable());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long minute = clock.instant().getEpochSecond() / 60;
        String key = request.getRemoteAddr() + ":" + minute;
        long count = increment(key, minute);
        if (count > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"API rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private long increment(String key, long minute) {
        return redisTemplate.map(template -> {
            String redisKey = "helium:rate-limit:" + key;
            Long count = template.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                template.expire(redisKey, Duration.ofMinutes(2));
            }
            return count == null ? 1L : count;
        }).orElseGet(() -> {
            int count = buckets.computeIfAbsent(key, ignored -> new Bucket(minute)).count.incrementAndGet();
            buckets.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
            return (long) count;
        });
    }

    private record Bucket(long minute, AtomicInteger count) {
        private Bucket(long minute) {
            this(minute, new AtomicInteger());
        }
    }
}
