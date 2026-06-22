package com.helium.core.authuser.application;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptHistoryService {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public LoginAttemptHistoryService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void record(UUID userId, String email, boolean success, String failureReason, SecurityContextData context) {
        jdbcTemplate.update(
            """
            insert into login_attempts (
                id, user_id, email, success, failure_reason, ip_address, device_info, user_agent, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            email,
            success,
            failureReason,
            context.ipAddress(),
            context.deviceInfo(),
            context.userAgent(),
            Timestamp.from(clock.instant())
        );
    }
}
