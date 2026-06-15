package com.helium.core.app.api;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiReadService {
    private final JdbcTemplate jdbcTemplate;

    public ApiReadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public UserDto user(UUID userId, Set<String> roles) {
        return jdbcTemplate.queryForObject(
            "select id, email, display_name, status, email_verified_at, created_at from auth_user_accounts where id = ?",
            (rs, rowNum) -> user(rs, roles),
            userId
        );
    }

    @Transactional(readOnly = true)
    public List<UserDto> users() {
        return jdbcTemplate.query(
            """
            select account.id, account.email, account.display_name, account.status, account.email_verified_at, account.created_at,
                   coalesce(string_agg(role.role, ',' order by role.role) filter (where role.revoked_at is null), '') as roles
            from auth_user_accounts account
            left join auth_role_grants role on role.user_id = account.id
            group by account.id
            order by account.created_at desc
            """,
            (rs, rowNum) -> new UserDto(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getTimestamp("email_verified_at") != null,
                splitRoles(rs.getString("roles")),
                rs.getTimestamp("created_at").toInstant()
            )
        );
    }

    @Transactional(readOnly = true)
    public List<BalanceDto> balances(UUID userId) {
        return jdbcTemplate.query(
            """
            select account.asset_code,
                   sum(case when account.balance_type = 'AVAILABLE' then snapshot.current_balance else 0 end) as available,
                   sum(case when account.balance_type = 'LOCKED' then snapshot.current_balance else 0 end) as locked
            from ledger_accounts account
            join ledger_balance_snapshots snapshot on snapshot.account_id = account.id
            where account.owner_type = 'USER' and account.owner_id = ?
            group by account.asset_code
            order by account.asset_code
            """,
            (rs, rowNum) -> new BalanceDto(rs.getString("asset_code"), rs.getBigDecimal("available"), rs.getBigDecimal("locked")),
            userId.toString()
        );
    }

    @Transactional(readOnly = true)
    public List<DepositDto> deposits(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, asset_code, network_code, tx_hash, output_index, amount, confirmations, status, detected_at
            from wallet_deposits
            where user_id = ?
            order by detected_at desc
            """,
            (rs, rowNum) -> new DepositDto(
                rs.getObject("id", UUID.class),
                rs.getString("asset_code"),
                rs.getString("network_code"),
                rs.getString("tx_hash"),
                rs.getInt("output_index"),
                rs.getBigDecimal("amount"),
                rs.getInt("confirmations"),
                rs.getString("status"),
                rs.getTimestamp("detected_at").toInstant()
            ),
            userId
        );
    }

    @Transactional(readOnly = true)
    public List<WithdrawalDto> withdrawals(UUID userId) {
        return withdrawals("where user_id = ?", userId);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalDto> pendingWithdrawals() {
        return withdrawals("where status in ('REQUESTED', 'APPROVED')");
    }

    @Transactional(readOnly = true)
    public List<AddressDto> addresses(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, asset_code, network_code, address, memo, status, created_at
            from wallet_deposit_addresses
            where user_id = ?
            order by created_at desc
            """,
            (rs, rowNum) -> new AddressDto(
                rs.getObject("id", UUID.class),
                rs.getString("asset_code"),
                rs.getString("network_code"),
                rs.getString("address"),
                rs.getString("memo"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            ),
            userId
        );
    }

    @Transactional(readOnly = true)
    public List<TradeDto> tradeHistory(UUID userId) {
        return jdbcTemplate.query(
            """
            select settlement.execution_id, settlement.market_symbol, settlement.quantity, settlement.price,
                   settlement.buyer_fee_amount, settlement.seller_fee_amount, settlement.created_at,
                   case when settlement.buyer_order_id = orders.id then 'BUY' else 'SELL' end as side
            from trading_settlement_instructions settlement
            join trading_orders orders on orders.id in (settlement.buyer_order_id, settlement.seller_order_id)
            where orders.user_id = ?
            order by settlement.created_at desc
            """,
            (rs, rowNum) -> new TradeDto(
                rs.getString("execution_id"),
                rs.getString("market_symbol"),
                rs.getString("side"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("BUY".equals(rs.getString("side")) ? "buyer_fee_amount" : "seller_fee_amount"),
                rs.getTimestamp("created_at").toInstant()
            ),
            userId
        );
    }

    @Transactional(readOnly = true)
    public List<AuditDto> adminAudit() {
        return jdbcTemplate.query(
            "select id, action, actor_id, target_type, target_id, details, occurred_at from admin_audit_events order by occurred_at desc limit 200",
            (rs, rowNum) -> new AuditDto(
                rs.getObject("id", UUID.class),
                rs.getString("action"),
                rs.getString("actor_id"),
                rs.getString("target_type") + ":" + rs.getString("target_id"),
                rs.getString("details"),
                rs.getTimestamp("occurred_at").toInstant()
            )
        );
    }

    @Transactional(readOnly = true)
    public List<ReconciliationDto> reconciliations() {
        return jdbcTemplate.query(
            "select id, report_type, status, scope_key, difference, created_at from admin_reconciliation_reports order by created_at desc limit 200",
            (rs, rowNum) -> new ReconciliationDto(
                rs.getObject("id", UUID.class),
                rs.getString("report_type"),
                rs.getString("status"),
                rs.getString("scope_key"),
                rs.getBigDecimal("difference"),
                rs.getTimestamp("created_at").toInstant()
            )
        );
    }

    @Transactional(readOnly = true)
    public List<ReconciliationDiscrepancyDto> reconciliationDiscrepancies() {
        return jdbcTemplate.query(
            """
            select id, report_id, severity, scope_key, details, difference, detected_at
            from admin_reconciliation_discrepancies
            order by detected_at desc
            limit 200
            """,
            (rs, rowNum) -> new ReconciliationDiscrepancyDto(
                rs.getObject("id", UUID.class),
                rs.getObject("report_id", UUID.class),
                rs.getString("severity"),
                rs.getString("scope_key"),
                rs.getString("details"),
                rs.getBigDecimal("difference"),
                rs.getTimestamp("detected_at").toInstant()
            )
        );
    }

    @Transactional(readOnly = true)
    public List<AdminMarketDto> adminMarkets() {
        return jdbcTemplate.query(
            """
            select market.symbol, market.enabled,
                   coalesce(schedule.maker_fee_rate, 0) as maker_fee_rate,
                   coalesce(schedule.taker_fee_rate, 0) as taker_fee_rate
            from trading_markets market
            left join trading_fee_schedules schedule on schedule.market_symbol = market.symbol and schedule.enabled = true
            order by market.symbol
            """,
            (rs, rowNum) -> new AdminMarketDto(
                rs.getString("symbol"),
                rs.getBoolean("enabled"),
                !rs.getBoolean("enabled"),
                rs.getBigDecimal("maker_fee_rate"),
                rs.getBigDecimal("taker_fee_rate")
            )
        );
    }

    private List<WithdrawalDto> withdrawals(String whereClause, Object... args) {
        return jdbcTemplate.query(
            """
            select id, client_request_id, user_id, asset_code, network_code, destination_address, destination_memo,
                   amount, fee, status, requested_at, broadcast_tx_hash
            from wallet_withdrawals
            """ + whereClause + " order by requested_at desc",
            (rs, rowNum) -> new WithdrawalDto(
                rs.getObject("id", UUID.class),
                rs.getString("client_request_id"),
                rs.getObject("user_id", UUID.class),
                rs.getString("asset_code"),
                rs.getString("network_code"),
                rs.getString("destination_address"),
                rs.getString("destination_memo"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("fee"),
                rs.getString("status"),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getString("broadcast_tx_hash")
            ),
            args
        );
    }

    private UserDto user(ResultSet rs, Set<String> roles) throws SQLException {
        return new UserDto(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getTimestamp("email_verified_at") != null,
            roles,
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private Set<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Set.of(roles.split(","));
    }

    public record UserDto(UUID id, String email, String displayName, String status, boolean emailVerified, Set<String> roles, Instant createdAt) {}
    public record BalanceDto(String asset, BigDecimal available, BigDecimal locked) {}
    public record DepositDto(UUID id, String asset, String network, String txHash, int outputIndex, BigDecimal amount, int confirmations, String status, Instant createdAt) {}
    public record WithdrawalDto(UUID id, String clientRequestId, UUID userId, String asset, String network, String destination, String memo, BigDecimal amount, BigDecimal fee, String status, Instant createdAt, String txHash) {}
    public record AddressDto(UUID id, String asset, String network, String address, String memo, String status, Instant createdAt) {}
    public record TradeDto(String executionId, String market, String side, BigDecimal price, BigDecimal quantity, BigDecimal fee, Instant time) {}
    public record AuditDto(UUID id, String action, String actorId, String target, String details, Instant occurredAt) {}
    public record ReconciliationDto(UUID id, String type, String status, String scope, BigDecimal difference, Instant createdAt) {}
    public record ReconciliationDiscrepancyDto(UUID id, UUID reportId, String severity, String scope, String details, BigDecimal difference, Instant detectedAt) {}
    public record AdminMarketDto(String symbol, boolean enabled, boolean halted, BigDecimal makerFeeRate, BigDecimal takerFeeRate) {}
}
