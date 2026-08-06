package com.helium.core.trading.application;

import com.helium.core.matching.application.MatchingCommandPort;
import com.helium.core.matching.application.TrustedTradingActorIssuer;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.OrderRepository;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that expires {@code TimeInForce.DAY} orders when their
 * market's business day closes.
 *
 * <p>The scheduler runs every minute and checks each enabled market's
 * {@code dayCloseTimeUtc}. If the current UTC time has passed the close
 * time, all open DAY orders in that market are expired through the
 * matching engine.
 */
@Service
public class DayOrderExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DayOrderExpirationScheduler.class);

    private final MarketRepository marketRepository;
    private final OrderRepository orderRepository;
    private final ObjectProvider<MatchingCommandPort> matchingCommandPortProvider;
    private final TrustedTradingActorIssuer tradingActorIssuer;
    private final String tradingPermission;
    private final Clock clock;

    public DayOrderExpirationScheduler(
        MarketRepository marketRepository,
        OrderRepository orderRepository,
        ObjectProvider<MatchingCommandPort> matchingCommandPortProvider,
        TrustedTradingActorIssuer tradingActorIssuer,
        @Value("${helium.trading.actor-permission:local-dev-trading-permission}") String tradingPermission,
        Clock clock
    ) {
        this.marketRepository = marketRepository;
        this.orderRepository = orderRepository;
        this.matchingCommandPortProvider = matchingCommandPortProvider;
        this.tradingActorIssuer = tradingActorIssuer;
        this.tradingPermission = tradingPermission;
        this.clock = clock;
    }

    /**
     * Runs every minute. For each enabled market, checks if the current UTC
     * time has passed or equals the market's day close time.
     * If so, finds all open DAY orders and sends them to the matching engine
     * for expiration.
     */
    @Scheduled(cron = "0 * * * * *")
    public void expireDayOrders() {
        LocalTime nowUtc = LocalTime.now(clock.withZone(ZoneOffset.UTC));
        List<Market> markets = marketRepository.findByEnabledTrueOrderBySymbolAsc();

        for (Market market : markets) {
            LocalTime closeTime = market.dayCloseTimeUtc();
            if (isAtOrPastCloseTime(nowUtc, closeTime)) {
                expireMarketDayOrders(market.symbol());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireMarketDayOrders(String marketSymbol) {
        List<Order> dayOrders = orderRepository.findOpenDayOrdersForUpdate(marketSymbol);
        if (dayOrders.isEmpty()) {
            return;
        }

        log.info("Expiring {} DAY orders for market {} at market close", dayOrders.size(), marketSymbol);

        MatchingCommandPort matchingCommandPort = matchingCommandPortProvider.getIfAvailable();
        if (matchingCommandPort == null) {
            log.warn("MatchingCommandPort not available, skipping DAY order expiration for {}", marketSymbol);
            return;
        }

        for (Order order : dayOrders) {
            try {
                withTradingActor(() -> matchingCommandPort.expireOrder(
                    new MatchingCommandPort.ExpireOrderCommand(order.id(), order.marketSymbol())
                ));
            } catch (Exception ex) {
                log.error("Failed to expire DAY order {} in market {}", order.id(), marketSymbol, ex);
            }
        }
    }

    /**
     * Checks if the current time is within the close-time minute window.
     * This prevents repeated expiration across multiple scheduler ticks.
     * The window is: [closeTime, closeTime + 1 minute).
     */
    private boolean isAtOrPastCloseTime(LocalTime now, LocalTime closeTime) {
        // Check if 'now' is within the same minute as closeTime
        return now.getHour() == closeTime.getHour()
            && now.getMinute() == closeTime.getMinute();
    }

    private void withTradingActor(Runnable operation) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(tradingActorIssuer.issueTradingActor(tradingPermission));
            operation.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
