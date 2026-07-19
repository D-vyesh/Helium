package com.helium.core.app.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.authuser.application.SecurityContextData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> THEMES = Set.of("SYSTEM", "DARK", "LIGHT");
    private static final Set<String> CHART_INTERVALS = Set.of("1m", "5m", "15m", "30m", "1H", "4H", "1D", "1W", "1M");
    private static final Set<String> CHART_STYLES = Set.of("CANDLES", "BARS", "LINE");
    private static final Set<String> SIDEBAR_LAYOUTS = Set.of("EXPANDED", "COMPACT", "COLLAPSED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LiveMarketDataService marketData;
    private final NotificationWebSocketHandler webSocketHandler;
    private final AccountActivityService activityService;
    private final Clock clock;

    public UserPreferenceService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        LiveMarketDataService marketData,
        NotificationWebSocketHandler webSocketHandler,
        AccountActivityService activityService,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.marketData = marketData;
        this.webSocketHandler = webSocketHandler;
        this.activityService = activityService;
        this.clock = clock;
    }

    @Transactional
    public UserPreferenceView get(UUID userId) {
        ensureExists(userId);
        return find(userId);
    }

    @Transactional
    public UserPreferenceView update(UUID userId, PreferenceUpdate update, SecurityContextData securityContext) {
        PreferenceUpdate normalized = normalize(update);
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into user_preferences (
                user_id, theme, timezone, language, preferred_fiat, chart_interval, chart_style,
                default_market, sidebar_layout, workspace_layout, order_defaults,
                notification_preferences, created_at, updated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?)
            on conflict (user_id) do update
                set theme = excluded.theme,
                    timezone = excluded.timezone,
                    language = excluded.language,
                    preferred_fiat = excluded.preferred_fiat,
                    chart_interval = excluded.chart_interval,
                    chart_style = excluded.chart_style,
                    default_market = excluded.default_market,
                    sidebar_layout = excluded.sidebar_layout,
                    workspace_layout = excluded.workspace_layout,
                    order_defaults = excluded.order_defaults,
                    notification_preferences = excluded.notification_preferences,
                    updated_at = excluded.updated_at
            """,
            userId,
            normalized.theme(),
            normalized.timezone(),
            normalized.language(),
            normalized.preferredFiat(),
            normalized.chartInterval(),
            normalized.chartStyle(),
            normalized.defaultMarket(),
            normalized.sidebarLayout(),
            json(normalized.workspaceLayout()),
            json(normalized.orderDefaults()),
            json(normalized.notificationPreferences()),
            timestamp(now),
            timestamp(now)
        );

        UserPreferenceView view = find(userId);
        activityService.record(
            userId,
            "SETTINGS",
            "PREFERENCES_UPDATED",
            "Exchange preferences updated",
            "SUCCESS",
            userId.toString(),
            securityContext,
            Map.of(
                "theme", view.theme(),
                "defaultMarket", view.defaultMarket(),
                "chartInterval", view.chartInterval(),
                "preferredFiat", view.preferredFiat()
            )
        );
        webSocketHandler.broadcast(userId, "preferences", view);
        return view;
    }

    private void ensureExists(UUID userId) {
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from user_preferences where user_id = ?",
            Integer.class,
            userId
        );
        if (exists != null && exists > 0) {
            return;
        }
        PreferenceUpdate fallback = defaults();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into user_preferences (
                user_id, theme, timezone, language, preferred_fiat, chart_interval, chart_style,
                default_market, sidebar_layout, workspace_layout, order_defaults,
                notification_preferences, created_at, updated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?)
            on conflict (user_id) do nothing
            """,
            userId,
            fallback.theme(),
            fallback.timezone(),
            fallback.language(),
            fallback.preferredFiat(),
            fallback.chartInterval(),
            fallback.chartStyle(),
            fallback.defaultMarket(),
            fallback.sidebarLayout(),
            json(fallback.workspaceLayout()),
            json(fallback.orderDefaults()),
            json(fallback.notificationPreferences()),
            timestamp(now),
            timestamp(now)
        );
    }

    private UserPreferenceView find(UUID userId) {
        return jdbcTemplate.queryForObject(
            """
            select user_id, theme, timezone, language, preferred_fiat, chart_interval, chart_style,
                   default_market, sidebar_layout, workspace_layout::text, order_defaults::text,
                   notification_preferences::text, updated_at
            from user_preferences
            where user_id = ?
            """,
            (rs, rowNum) -> preference(rs),
            userId
        );
    }

    private UserPreferenceView preference(ResultSet rs) throws SQLException {
        return new UserPreferenceView(
            rs.getObject("user_id", UUID.class),
            rs.getString("theme"),
            rs.getString("timezone"),
            rs.getString("language"),
            rs.getString("preferred_fiat"),
            rs.getString("chart_interval"),
            rs.getString("chart_style"),
            rs.getString("default_market"),
            rs.getString("sidebar_layout"),
            map(rs.getString("workspace_layout")),
            map(rs.getString("order_defaults")),
            map(rs.getString("notification_preferences")),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private PreferenceUpdate normalize(PreferenceUpdate input) {
        PreferenceUpdate fallback = defaults();
        String theme = enumValue(input.theme(), fallback.theme(), THEMES, "theme");
        String timezone = text(input.timezone(), fallback.timezone(), 80, "timezone");
        try {
            ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("timezone is not valid");
        }
        String language = text(input.language(), fallback.language(), 16, "language").toLowerCase(Locale.ROOT);
        String preferredFiat = text(input.preferredFiat(), fallback.preferredFiat(), 12, "preferredFiat").toUpperCase(Locale.ROOT);
        String chartInterval = text(input.chartInterval(), fallback.chartInterval(), 16, "chartInterval");
        if (!CHART_INTERVALS.contains(chartInterval)) {
            throw new IllegalArgumentException("chartInterval is not supported");
        }
        String chartStyle = enumValue(input.chartStyle(), fallback.chartStyle(), CHART_STYLES, "chartStyle");
        String defaultMarket = marketSymbol(input.defaultMarket(), fallback.defaultMarket());
        String sidebarLayout = enumValue(input.sidebarLayout(), fallback.sidebarLayout(), SIDEBAR_LAYOUTS, "sidebarLayout");
        return new PreferenceUpdate(
            theme,
            timezone,
            language,
            preferredFiat,
            chartInterval,
            chartStyle,
            defaultMarket,
            sidebarLayout,
            merge(fallback.workspaceLayout(), input.workspaceLayout()),
            merge(fallback.orderDefaults(), input.orderDefaults()),
            merge(fallback.notificationPreferences(), input.notificationPreferences())
        );
    }

    private String marketSymbol(String value, String fallback) {
        String symbol = text(value, fallback, 40, "defaultMarket").replace("-", "").toUpperCase(Locale.ROOT);
        if (!marketData.supports(symbol)) {
            throw new IllegalArgumentException("defaultMarket is not supported");
        }
        return symbol;
    }

    private String enumValue(String value, String fallback, Set<String> allowed, String field) {
        String normalized = text(value, fallback, 40, field).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " is not supported");
        }
        return normalized;
    }

    private String text(String value, String fallback, int max, String field) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        if (text.length() > max) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return text;
    }

    private Map<String, Object> merge(Map<String, Object> fallback, Map<String, Object> override) {
        Map<String, Object> merged = new LinkedHashMap<>(fallback);
        if (override != null) {
            override.forEach((key, value) -> {
                if (key != null && !key.isBlank() && key.length() <= 80 && value != null) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    private PreferenceUpdate defaults() {
        return new PreferenceUpdate(
            "SYSTEM",
            "UTC",
            "en",
            "USD",
            "1m",
            "CANDLES",
            marketData.supports("BTCUSDT") ? "BTCUSDT" : marketData.markets().stream().findFirst().map(LiveMarketDataModels.MarketView::symbol).orElse("BTCUSDT"),
            "EXPANDED",
            Map.of("leftCollapsed", false, "rightCollapsed", false, "bottomCollapsed", false),
            Map.of("side", "BUY", "type", "LIMIT", "timeInForce", "GTC"),
            Map.of(
                "inApp", true,
                "email", false,
                "push", false,
                "orders", true,
                "trades", true,
                "security", true,
                "market", true
            )
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("preferences payload is not serializable", exception);
        }
    }

    private Map<String, Object> map(String value) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? "{}" : value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("preferences payload is invalid", exception);
        }
    }

    public record PreferenceUpdate(
        String theme,
        String timezone,
        String language,
        String preferredFiat,
        String chartInterval,
        String chartStyle,
        String defaultMarket,
        String sidebarLayout,
        Map<String, Object> workspaceLayout,
        Map<String, Object> orderDefaults,
        Map<String, Object> notificationPreferences
    ) {}

    public record UserPreferenceView(
        UUID userId,
        String theme,
        String timezone,
        String language,
        String preferredFiat,
        String chartInterval,
        String chartStyle,
        String defaultMarket,
        String sidebarLayout,
        Map<String, Object> workspaceLayout,
        Map<String, Object> orderDefaults,
        Map<String, Object> notificationPreferences,
        Instant updatedAt
    ) {}
}
