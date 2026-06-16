package com.helium.core.trading.application;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketMakerProgramService {
    private static final Logger log = LoggerFactory.getLogger(MarketMakerProgramService.class);

    private final Map<UUID, BigDecimal> dailyVolumeByMaker = new ConcurrentHashMap<>();

    public void recordMakerVolume(UUID makerId, BigDecimal volumeUsd) {
        dailyVolumeByMaker.merge(makerId, volumeUsd, BigDecimal::add);
    }

    public void executeEndOfDayRebates() {
        log.info("Executing End of Day Market Maker Rebate Batch...");
        dailyVolumeByMaker.forEach((makerId, volumeUsd) -> {
            if (volumeUsd.compareTo(new BigDecimal("10000000.00")) > 0) {
                // Tier 1: > $10M Volume gets a rebate
                // In a real system, this would trigger an AdjustmentRequest to the ledger
                BigDecimal rebate = volumeUsd.multiply(new BigDecimal("0.0001")); // 1 bps
                log.info("Maker {} qualified for Tier 1. Issuing {} USD rebate.", makerId, rebate);
            } else if (volumeUsd.compareTo(new BigDecimal("1000000.00")) > 0) {
                // Tier 2
                BigDecimal rebate = volumeUsd.multiply(new BigDecimal("0.00005")); // 0.5 bps
                log.info("Maker {} qualified for Tier 2. Issuing {} USD rebate.", makerId, rebate);
            }
        });
        
        // Reset volume for next day
        dailyVolumeByMaker.clear();
    }
}
