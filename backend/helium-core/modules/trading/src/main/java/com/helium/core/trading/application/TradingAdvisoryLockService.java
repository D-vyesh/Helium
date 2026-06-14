package com.helium.core.trading.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class TradingAdvisoryLockService {
    private final JdbcTemplate jdbcTemplate;

    TradingAdvisoryLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void lock(String namespace, String key) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtext(?), hashtext(?))",
            statement -> {
                statement.setString(1, namespace);
                statement.setString(2, key);
            },
            resultSet -> null
        );
    }
}
