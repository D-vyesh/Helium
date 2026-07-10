package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.SessionStatus;
import java.time.Instant;
import java.util.UUID;

public record SessionDetails(
    UUID id,
    String deviceName,
    String browser,
    String ipAddress,
    String userAgent,
    Instant createdAt,
    Instant lastSeenAt,
    Instant expiresAt,
    SessionStatus status,
    boolean current
) {}
