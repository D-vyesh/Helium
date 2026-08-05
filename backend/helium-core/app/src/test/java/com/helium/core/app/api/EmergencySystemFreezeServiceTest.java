package com.helium.core.app.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helium.core.admin.application.AdminAuditService;
import com.helium.core.admin.domain.AdminAuditAction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmergencySystemFreezeServiceTest {

    @Test
    void disablesTradingAndWithdrawalsAndRecordsAnAuditEvent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("trading_markets"), any(Object[].class))).thenReturn(3);
        when(jdbcTemplate.update(contains("wallet_assets"), any(Object[].class))).thenReturn(2);
        when(jdbcTemplate.update(contains("wallet_blockchain_networks"), any(Object[].class))).thenReturn(4);
        AdminAuditService auditService = mock(AdminAuditService.class);
        UUID actorId = UUID.randomUUID();

        EmergencySystemFreezeService service = new EmergencySystemFreezeService(
            jdbcTemplate,
            auditService,
            Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC)
        );

        EmergencySystemFreezeService.FreezeResult result = service.freeze(actorId, "operator initiated");

        assertThat(result).isEqualTo(new EmergencySystemFreezeService.FreezeResult(3, 2, 4));
        verify(auditService).record(
            eq(AdminAuditAction.TRADING_HALTED),
            eq(actorId.toString()),
            eq("SYSTEM"),
            eq("GLOBAL"),
            contains("operator initiated")
        );
    }
}
