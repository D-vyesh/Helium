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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final int MAX_IP_REQUESTS_PER_MINUTE = 300;
    private static final int MAX_USER_REQUESTS_PER_MINUTE = 600;
    private static final int MAX_WRITE_REQUESTS_PER_MINUTE = 60;
    private static final String WRITE_METHODS = "POST,PUT,DELETE,PATCH";

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
        String ip = request.getRemoteAddr();

        // IP-based rate limit
        String ipKey = "ip:" + ip + ":" + minute;
        long ipCount = increment(ipKey, minute);
        long ipLimit = MAX_IP_REQUESTS_PER_MINUTE;
        long ipRemaining = Math.max(0, ipLimit - ipCount);

        if (ipCount > ipLimit) {
            writeRateLimitResponse(response, ipRemaining, minute);
            return;
        }

        // User-based rate limit (if authenticated)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String userId) {
            String userKey = "user:" + userId + ":" + minute;
            long userCount = increment(userKey, minute);
            long userLimit = MAX_USER_REQUESTS_PER_MINUTE;
            long userRemaining = Math.max(0, userLimit - userCount);
            ipRemaining = Math.min(ipRemaining, userRemaining);

            if (userCount > userLimit) {
                writeRateLimitResponse(response, userRemaining, minute);
                return;
            }

            // Write operation limit
            if (WRITE_METHODS.contains(request.getMethod())) {
                String writeKey = "write:" + userId + ":" + minute;
                long writeCount = increment(writeKey, minute);
                long writeRemaining = Math.max(0, MAX_WRITE_REQUESTS_PER_MINUTE - writeCount);
                ipRemaining = Math.min(ipRemaining, writeRemaining);

                if (writeCount > MAX_WRITE_REQUESTS_PER_MINUTE) {
                    writeRateLimitResponse(response, writeRemaining, minute);
                    return;
                }
            }
        }

        // Set rate limit headers
        response.setHeader("X-RateLimit-Remaining", String.valueOf(ipRemaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf((minute + 1) * 60));

        filterChain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletResponse response, long remaining, long minute) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf((minute + 1) * 60));
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"API rate limit exceeded\"}");
    }

    private long increment(String key, long minute) {
        try {
            return redisTemplate.map(template -> {
                String redisKey = "helium:rate-limit:" + key;
                Long count = template.opsForValue().increment(redisKey);
                if (count != null && count == 1L) {
                    template.expire(redisKey, Duration.ofMinutes(2));
                }
                return count == null ? 1L : count;
            }).orElseGet(() -> incrementInMemory(key, minute));
        } catch (DataAccessException exception) {
            return incrementInMemory(key, minute);
        }
    }

    private long incrementInMemory(String key, long minute) {
        int count = buckets.computeIfAbsent(key, ignored -> new Bucket(minute)).count.incrementAndGet();
        buckets.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
        return (long) count;
    }

    private record Bucket(long minute, AtomicInteger count) {
        private Bucket(long minute) {
            this(minute, new AtomicInteger());
        }
    }
}
