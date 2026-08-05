package com.helium.core.app.api;

import com.helium.core.admin.application.AdminAuditService;
import com.helium.core.admin.domain.AdminAuditAction;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the persisted controls required for a full exchange trading/withdrawal freeze. */
@Service
public class EmergencySystemFreezeService {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;
    private final Clock clock;

    public EmergencySystemFreezeService(JdbcTemplate jdbcTemplate, AdminAuditService auditService, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public FreezeResult freeze(UUID actorId, String reason) {
        Instant now = clock.instant();
        int haltedMarkets = jdbcTemplate.update(
            "update trading_markets set enabled = false, updated_at = ? where enabled = true",
            Timestamp.from(now)
        );
        int haltedAssets = jdbcTemplate.update(
            "update wallet_assets set withdrawal_enabled = false, updated_at = ? where withdrawal_enabled = true",
            Timestamp.from(now)
        );
        int haltedNetworks = jdbcTemplate.update(
            "update wallet_blockchain_networks set withdrawal_enabled = false, updated_at = ? where withdrawal_enabled = true",
            Timestamp.from(now)
        );
        auditService.record(
            AdminAuditAction.TRADING_HALTED,
            actorId.toString(),
            "SYSTEM",
            "GLOBAL",
            "emergency freeze: " + reason
        );
        return new FreezeResult(haltedMarkets, haltedAssets, haltedNetworks);
    }

    public record FreezeResult(int haltedMarkets, int haltedAssets, int haltedNetworks) {}
}
