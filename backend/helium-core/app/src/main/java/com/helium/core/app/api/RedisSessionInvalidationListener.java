package com.helium.core.app.api;

import com.helium.core.authuser.application.SessionCachePort;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Listens to Redis pub/sub channel for session revocation events from other backend instances.
 * On receiving a revocation message, evicts local session cache entries for the affected user.
 * This enables distributed session invalidation across multi-instance deployments.
 */
@Configuration
public class RedisSessionInvalidationListener {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionInvalidationListener.class);
    private static final String REVOCATION_CHANNEL = "helium:sessions:revoked";

    private final ConcurrentMap<UUID, Instant> localRevocationCache = new ConcurrentHashMap<>();

    public RedisSessionInvalidationListener(
        ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
        ObjectProvider<SessionCachePort> sessionCachePortProvider
    ) {
        RedisConnectionFactory factory = connectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            log.info("Redis not available — session invalidation listener disabled");
            return;
        }

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(new RevocationListener(), new ChannelTopic(REVOCATION_CHANNEL));
        try {
            container.afterPropertiesSet();
            container.start();
            log.info("Redis session invalidation listener started on channel={}", REVOCATION_CHANNEL);
        } catch (Exception exception) {
            log.warn("Failed to start Redis session invalidation listener: {}", exception.getMessage());
        }
    }

    /**
     * Check if a user was revoked (from cross-instance pub/sub) after the given timestamp.
     */
    public boolean isUserRevokedLocally(UUID userId, Instant sessionCreatedAt) {
        Instant revokedAt = localRevocationCache.get(userId);
        return revokedAt != null && !revokedAt.isBefore(sessionCreatedAt);
    }

    private class RevocationListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                String body = new String(message.getBody());
                // Format: "userId:revokedAt"
                int separator = body.indexOf(':');
                if (separator <= 0) {
                    return;
                }
                UUID userId = UUID.fromString(body.substring(0, separator));
                Instant revokedAt = Instant.parse(body.substring(separator + 1));
                localRevocationCache.put(userId, revokedAt);
                log.debug("Session revocation received via pub/sub: userId={}, revokedAt={}", userId, revokedAt);
            } catch (RuntimeException exception) {
                log.warn("Failed to parse session revocation message: {}", exception.getMessage());
            }
        }
    }
}
