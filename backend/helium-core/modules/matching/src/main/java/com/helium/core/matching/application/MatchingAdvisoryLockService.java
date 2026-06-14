package com.helium.core.matching.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class MatchingAdvisoryLockService {
    private final JdbcTemplate jdbcTemplate;

    MatchingAdvisoryLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void lockMarket(String marketSymbol) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtext(?), hashtext(?))",
            statement -> {
                statement.setString(1, "matching:market");
                statement.setString(2, marketSymbol);
            },
            resultSet -> null
        );
    }
}
