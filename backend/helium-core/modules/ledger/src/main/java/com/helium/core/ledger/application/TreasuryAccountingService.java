package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TreasuryAccountingService {
    private static final Logger log = LoggerFactory.getLogger(TreasuryAccountingService.class);
    private final BalanceSnapshotRepository balanceSnapshotRepository;

    public TreasuryAccountingService(BalanceSnapshotRepository balanceSnapshotRepository) {
        this.balanceSnapshotRepository = balanceSnapshotRepository;
    }

    public Map<String, Object> calculateDailyNav() {
        log.info("Calculating Daily Net Asset Value (NAV)...");

        BigDecimal totalExternalAssets = balanceSnapshotRepository
            .totalBalanceByOwnerType(LedgerAccountOwnerType.EXTERNAL);
        BigDecimal totalUserLiabilities = balanceSnapshotRepository
            .totalBalanceByOwnerType(LedgerAccountOwnerType.USER);
        BigDecimal totalCollectedFees = balanceSnapshotRepository
            .totalBalanceByOwnerType(LedgerAccountOwnerType.FEE);

        BigDecimal calculatedNav = totalExternalAssets.subtract(totalUserLiabilities);

        if (calculatedNav.compareTo(totalCollectedFees) != 0) {
            log.warn("NAV reconciliation discrepancy. Ledger equity: {}, fee balances: {}",
                totalCollectedFees, calculatedNav);
        } else {
            log.info("NAV Reconciliation Successful. Exchange Equity: {}", calculatedNav);
        }

        return Map.of(
            "totalExternalAssets", totalExternalAssets,
            "totalUserLiabilities", totalUserLiabilities,
            "totalCollectedFees", totalCollectedFees,
            "dailyNav", calculatedNav,
            "timestamp", java.time.Instant.now().toString()
        );
    }
}
