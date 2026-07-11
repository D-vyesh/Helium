package com.helium.core.app.api;

import com.helium.core.app.api.LiveMarketDataModels.TickerResponse;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceAlertService {
    private static final MathContext MONEY_CONTEXT = new MathContext(38, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final JdbcTemplate jdbcTemplate;
    private final LiveMarketDataService marketData;
    private final NotificationService notificationService;
    private final Clock clock;

    public PriceAlertService(
        JdbcTemplate jdbcTemplate,
        LiveMarketDataService marketData,
        NotificationService notificationService,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.marketData = marketData;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PriceAlertView> list(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, user_id, market_symbol, condition_type, threshold, repeating, enabled,
                   delivery_in_app, delivery_email, delivery_push, expires_at, last_evaluated_price,
                   triggered_at, created_at, updated_at
            from price_alerts
            where user_id = ?
            order by created_at desc
            """,
            (rs, rowNum) -> alert(rs),
            userId
        );
    }

    @Transactional
    public PriceAlertView create(UUID userId, PriceAlertRequest request) {
        String market = normalizeMarket(request.marketSymbol());
        if (!marketData.supports(market)) {
            throw new IllegalArgumentException("unsupported market symbol");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into price_alerts (
                id, user_id, market_symbol, condition_type, threshold, repeating, enabled,
                delivery_in_app, delivery_email, delivery_push, expires_at, created_at, updated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            userId,
            market,
            request.conditionType(),
            request.threshold(),
            request.repeating(),
            request.enabled(),
            request.deliveryInApp(),
            request.deliveryEmail(),
            request.deliveryPush(),
            timestamp(request.expiresAt()),
            timestamp(now),
            timestamp(now)
        );
        return get(userId, id);
    }

    @Transactional
    public PriceAlertView setEnabled(UUID userId, UUID id, boolean enabled) {
        jdbcTemplate.update(
            "update price_alerts set enabled = ?, updated_at = ? where id = ? and user_id = ?",
            enabled,
            timestamp(clock.instant()),
            id,
            userId
        );
        return get(userId, id);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        jdbcTemplate.update("delete from price_alerts where id = ? and user_id = ?", id, userId);
    }

    @Scheduled(fixedDelayString = "${HELIUM_PRICE_ALERT_EVALUATION_MS:15000}")
    @Transactional
    public void evaluateEnabledAlerts() {
        List<PriceAlertView> alerts = jdbcTemplate.query(
            """
            select id, user_id, market_symbol, condition_type, threshold, repeating, enabled,
                   delivery_in_app, delivery_email, delivery_push, expires_at, last_evaluated_price,
                   triggered_at, created_at, updated_at
            from price_alerts
            where enabled = true and (expires_at is null or expires_at > ?)
            order by created_at asc
            """,
            (rs, rowNum) -> alert(rs),
            timestamp(clock.instant())
        );
        alerts.forEach(this::evaluate);
    }

    private void evaluate(PriceAlertView alert) {
        marketData.ticker(alert.marketSymbol()).ifPresent(ticker -> {
            BigDecimal current = ticker.lastPrice();
            boolean triggered = triggered(alert, ticker);
            jdbcTemplate.update(
                """
                update price_alerts
                set last_evaluated_price = ?,
                    triggered_at = case when ? then ? else triggered_at end,
                    enabled = case when ? and repeating = false then false else enabled end,
                    updated_at = ?
                where id = ?
                """,
                current,
                triggered,
                timestamp(clock.instant()),
                triggered,
                timestamp(clock.instant()),
                alert.id()
            );
            if (triggered && alert.deliveryInApp()) {
                notificationService.create(
                    alert.userId(),
                    "MARKET",
                    "PRICE_ALERT_TRIGGERED",
                    "Price alert triggered",
                    alert.marketSymbol() + " matched " + alert.conditionType() + " " + alert.threshold().stripTrailingZeros().toPlainString(),
                    Map.of(
                        "alertId", alert.id().toString(),
                        "marketSymbol", alert.marketSymbol(),
                        "conditionType", alert.conditionType(),
                        "threshold", alert.threshold(),
                        "lastPrice", current
                    )
                );
            }
        });
    }

    private boolean triggered(PriceAlertView alert, TickerResponse ticker) {
        BigDecimal current = ticker.lastPrice();
        BigDecimal previous = alert.lastEvaluatedPrice();
        return switch (alert.conditionType()) {
            case "PRICE_ABOVE" -> current.compareTo(alert.threshold()) > 0;
            case "PRICE_BELOW" -> current.compareTo(alert.threshold()) < 0;
            case "CROSSES_ABOVE" -> previous != null && previous.compareTo(alert.threshold()) <= 0 && current.compareTo(alert.threshold()) > 0;
            case "CROSSES_BELOW" -> previous != null && previous.compareTo(alert.threshold()) >= 0 && current.compareTo(alert.threshold()) < 0;
            case "CHANGE_PERCENT_ABOVE" -> percentChange(ticker).abs().compareTo(alert.threshold()) > 0;
            case "VOLUME_ABOVE" -> ticker.volume24h().compareTo(alert.threshold()) > 0;
            default -> false;
        };
    }

    private BigDecimal percentChange(TickerResponse ticker) {
        if (ticker.openPrice24h() == null || ticker.openPrice24h().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return ticker.lastPrice()
            .subtract(ticker.openPrice24h())
            .multiply(ONE_HUNDRED, MONEY_CONTEXT)
            .divide(ticker.openPrice24h(), 8, RoundingMode.HALF_UP);
    }

    private PriceAlertView get(UUID userId, UUID id) {
        return jdbcTemplate.queryForObject(
            """
            select id, user_id, market_symbol, condition_type, threshold, repeating, enabled,
                   delivery_in_app, delivery_email, delivery_push, expires_at, last_evaluated_price,
                   triggered_at, created_at, updated_at
            from price_alerts
            where id = ? and user_id = ?
            """,
            (rs, rowNum) -> alert(rs),
            id,
            userId
        );
    }

    private PriceAlertView alert(ResultSet rs) throws SQLException {
        return new PriceAlertView(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("market_symbol"),
            rs.getString("condition_type"),
            rs.getBigDecimal("threshold"),
            rs.getBoolean("repeating"),
            rs.getBoolean("enabled"),
            rs.getBoolean("delivery_in_app"),
            rs.getBoolean("delivery_email"),
            rs.getBoolean("delivery_push"),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
            rs.getBigDecimal("last_evaluated_price"),
            rs.getTimestamp("triggered_at") == null ? null : rs.getTimestamp("triggered_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String normalizeMarket(String marketSymbol) {
        return marketSymbol == null ? "" : marketSymbol.trim().toUpperCase(Locale.ROOT).replace("-", "");
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public record PriceAlertRequest(
        String marketSymbol,
        String conditionType,
        BigDecimal threshold,
        boolean repeating,
        boolean enabled,
        boolean deliveryInApp,
        boolean deliveryEmail,
        boolean deliveryPush,
        Instant expiresAt
    ) {}

    public record PriceAlertView(
        UUID id,
        UUID userId,
        String marketSymbol,
        String conditionType,
        BigDecimal threshold,
        boolean repeating,
        boolean enabled,
        boolean deliveryInApp,
        boolean deliveryEmail,
        boolean deliveryPush,
        Instant expiresAt,
        BigDecimal lastEvaluatedPrice,
        Instant triggeredAt,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
