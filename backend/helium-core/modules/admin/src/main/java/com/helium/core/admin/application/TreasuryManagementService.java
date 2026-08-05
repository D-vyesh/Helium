package com.helium.core.admin.application;

import com.helium.core.ledger.application.TreasuryAccountingService;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TreasuryManagementService {
    private static final Logger log = LoggerFactory.getLogger(TreasuryManagementService.class);

    private final TreasuryAccountingService treasuryAccountingService;

    public TreasuryManagementService(TreasuryAccountingService treasuryAccountingService) {
        this.treasuryAccountingService = treasuryAccountingService;
    }

    public void monitorWalletFragmentation(String assetCode) {
        Map<String, Object> nav = treasuryAccountingService.calculateDailyNav();
        BigDecimal totalReserves = (BigDecimal) nav.get("totalExternalAssets");
        BigDecimal totalUserLiabilities = (BigDecimal) nav.get("totalUserLiabilities");

        if (totalReserves.compareTo(totalUserLiabilities) < 0) {
            log.error("CRITICAL ALARM: Ledger reserve coverage is negative for {}. Liabilities ({}) > external assets ({})",
                assetCode,
                totalUserLiabilities, totalReserves);
        }

        log.info(
            "Ledger reserve coverage for {}: external assets={}, user liabilities={}. "
                + "Hot/cold fragmentation requires an authenticated custody balance provider.",
            assetCode,
            totalReserves,
            totalUserLiabilities
        );
    }
}
