package com.helium.core.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.helium.core.trading.domain.FeeAssetType;
import com.helium.core.trading.domain.FeeSchedule;
import com.helium.core.trading.infrastructure.FeeScheduleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class FeeScheduleUpdateCommandHandler implements GovernanceCommandHandler {

    private final FeeScheduleRepository feeScheduleRepository;
    private final Clock clock;

    public FeeScheduleUpdateCommandHandler(FeeScheduleRepository feeScheduleRepository, Clock clock) {
        this.feeScheduleRepository = feeScheduleRepository;
        this.clock = clock;
    }

    @Override
    public String supportedRequestType() {
        return "FEE_SCHEDULE_UPDATE";
    }

    @Override
    public void execute(JsonNode payload) {
        String marketSymbol = payload.path("marketSymbol").asText();
        BigDecimal makerFeeRate = new BigDecimal(payload.path("makerFeeRate").asText());
        BigDecimal takerFeeRate = new BigDecimal(payload.path("takerFeeRate").asText());
        
        String sellFeeAssetStr = payload.path("sellFeeAsset").asText("SELL_ASSET");
        FeeAssetType sellFeeAsset = FeeAssetType.valueOf(sellFeeAssetStr);
        boolean enabled = payload.path("enabled").asBoolean(true);

        FeeSchedule feeSchedule = feeScheduleRepository.findByMarketSymbol(marketSymbol)
            .map(existing -> {
                existing.update(makerFeeRate, takerFeeRate, sellFeeAsset, enabled, clock.instant());
                return existing;
            })
            .orElseGet(() -> FeeSchedule.configure(marketSymbol, makerFeeRate, takerFeeRate, sellFeeAsset, enabled, clock.instant()));

        feeScheduleRepository.save(feeSchedule);
    }
}
