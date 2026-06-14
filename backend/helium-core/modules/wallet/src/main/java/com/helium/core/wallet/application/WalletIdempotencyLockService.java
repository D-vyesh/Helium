package com.helium.core.wallet.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WalletIdempotencyLockService {
    private final JdbcTemplate jdbcTemplate;

    public WalletIdempotencyLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(String namespace, String key) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtext(?), hashtext(?))",
            preparedStatement -> {
                preparedStatement.setString(1, namespace);
                preparedStatement.setString(2, key);
            },
            resultSet -> null
        );
    }
}

