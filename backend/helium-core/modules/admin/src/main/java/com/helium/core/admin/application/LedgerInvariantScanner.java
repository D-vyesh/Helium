package com.helium.core.admin.application;

import com.helium.core.ledger.application.TreasuryAccountingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class LedgerInvariantScanner {
    private static final Logger log = LoggerFactory.getLogger(LedgerInvariantScanner.class);

    private final TreasuryAccountingService treasuryAccountingService;
    private final ExchangeStatusService exchangeStatusService;

    public LedgerInvariantScanner(TreasuryAccountingService treasuryAccountingService, ExchangeStatusService exchangeStatusService) {
        this.treasuryAccountingService = treasuryAccountingService;
        this.exchangeStatusService = exchangeStatusService;
    }

    public void scanLedgerInvariants() {
        log.info("Running background Formal Verification: Ledger Invariants (Assets = Liabilities + Equity)");
        Map<String, Object> navReport = treasuryAccountingService.calculateDailyNav();
        
        BigDecimal assets = (BigDecimal) navReport.get("totalExternalAssets");
        BigDecimal liabilities = (BigDecimal) navReport.get("totalUserLiabilities");
        BigDecimal fees = (BigDecimal) navReport.get("totalCollectedFees");

        // The equation is: Assets - Liabilities = Exchange Equity (Fees collected, etc)
        // So Assets - Liabilities - Fees must exactly equal ZERO.
        BigDecimal checksum = assets.subtract(liabilities).subtract(fees);

        if (checksum.compareTo(BigDecimal.ZERO) != 0) {
            log.error("CRITICAL INVARIANT VIOLATION: Conservation of value broken. Checksum: {}", checksum);
            triggerExchangeHalt();
        } else {
            log.debug("Invariant check passed: Total Assets correctly map to Liabilities and Equity.");
        }
    }

    private void triggerExchangeHalt() {
        log.error("SYSTEM HALT: Emitting global outage signal to protect real-money assets.");
        exchangeStatusService.updateComponentStatus("matching-engine", "OUTAGE");
        exchangeStatusService.updateComponentStatus("ledger", "OUTAGE");
    }
}
