package com.helium.core.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.helium.core.trading.domain.Market;
import com.helium.core.trading.infrastructure.MarketRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class MarketListingCommandHandler implements GovernanceCommandHandler {

    private final MarketRepository marketRepository;
    private final Clock clock;

    public MarketListingCommandHandler(MarketRepository marketRepository, Clock clock) {
        this.marketRepository = marketRepository;
        this.clock = clock;
    }

    @Override
    public String supportedRequestType() {
        return "MARKET_LISTING";
    }

    @Override
    public void execute(JsonNode payload) {
        String symbol = payload.path("symbol").asText();
        String baseAsset = payload.path("baseAsset").asText();
        String quoteAsset = payload.path("quoteAsset").asText();
        int priceScale = payload.path("priceScale").asInt(2);
        int quantityScale = payload.path("quantityScale").asInt(4);
        BigDecimal minOrderQuantity = new BigDecimal(payload.path("minOrderQuantity").asText("0.0001"));
        BigDecimal minNotional = new BigDecimal(payload.path("minNotional").asText("1.0"));
        boolean enabled = payload.path("enabled").asBoolean(true);

        Market market = marketRepository.findById(symbol)
            .map(existing -> {
                existing.updatePolicy(enabled, clock.instant());
                return existing;
            })
            .orElseGet(() -> Market.register(
                symbol,
                baseAsset,
                quoteAsset,
                priceScale,
                quantityScale,
                minOrderQuantity,
                minNotional,
                enabled,
                clock.instant()
            ));

        marketRepository.save(market);
    }
}
