package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.LoginAttemptThrottle;
import com.helium.core.authuser.infrastructure.LoginAttemptThrottleRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptThrottleService {
    private static final int SOURCE_FAILURE_THRESHOLD = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final LoginAttemptThrottleRepository repository;
    private final TokenCodec tokenCodec;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public LoginAttemptThrottleService(
        LoginAttemptThrottleRepository repository,
        TokenCodec tokenCodec,
        JdbcTemplate jdbcTemplate,
        Clock clock
    ) {
        this.repository = repository;
        this.tokenCodec = tokenCodec;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String normalizedEmail, String ipAddress) {
        return repository.findReadBySubjectHashAndSourceHash(hash(normalizedEmail), hash(ipAddress))
            .map(throttle -> throttle.isBlocked(clock.instant()))
            .orElse(false);
    }

    @Transactional
    public boolean recordFailure(String normalizedEmail, String ipAddress) {
        String subjectHash = hash(normalizedEmail);
        String sourceHash = hash(ipAddress);
        lock(subjectHash, sourceHash);
        LoginAttemptThrottle throttle = repository.findBySubjectHashAndSourceHash(subjectHash, sourceHash)
            .orElseGet(() -> LoginAttemptThrottle.create(subjectHash, sourceHash, clock.instant()));
        boolean blocked = throttle.recordFailure(SOURCE_FAILURE_THRESHOLD, FAILURE_WINDOW, BLOCK_DURATION, clock.instant());
        repository.save(throttle);
        return blocked;
    }

    @Transactional
    public void clear(String normalizedEmail, String ipAddress) {
        String subjectHash = hash(normalizedEmail);
        String sourceHash = hash(ipAddress);
        lock(subjectHash, sourceHash);
        repository.deleteBySubjectHashAndSourceHash(subjectHash, sourceHash);
    }

    private String hash(String value) {
        return tokenCodec.hash(value);
    }

    private void lock(String subjectHash, String sourceHash) {
        jdbcTemplate.queryForList(
            "select pg_advisory_xact_lock(hashtext('helium-auth-login-throttle'), hashtext(?))",
            subjectHash + sourceHash
        );
    }
}
