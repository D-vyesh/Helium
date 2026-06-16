package com.helium.core.ledger.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class InsuranceFundService {
    private static final Logger log = LoggerFactory.getLogger(InsuranceFundService.class);

    private final LedgerPostingPort ledgerPostingPort;
    private final AtomicReference<BigDecimal> fundBalance = new AtomicReference<>(new BigDecimal("1000000.00")); // Seed amount

    public InsuranceFundService(LedgerPostingPort ledgerPostingPort) {
        this.ledgerPostingPort = ledgerPostingPort;
    }

    public void absorbLiquidationLoss(BigDecimal lossAmount) {
        log.warn("ADL Triggered: Absorbing liquidation loss of {} from Insurance Fund", lossAmount);
        BigDecimal current = fundBalance.updateAndGet(balance -> balance.subtract(lossAmount));
        
        if (current.compareTo(BigDecimal.ZERO) < 0) {
            log.error("CRITICAL: Insurance fund depleted! Triggering ADL deleveraging.");
            // In a real system, we would trigger Auto-Deleveraging (ADL) of profitable positions
        }
    }

    public void injectLiquidationSurplus(BigDecimal surplusAmount) {
        log.info("Injecting liquidation surplus of {} to Insurance Fund", surplusAmount);
        fundBalance.updateAndGet(balance -> balance.add(surplusAmount));
    }

    public BigDecimal getCurrentBalance() {
        return fundBalance.get();
    }
}
