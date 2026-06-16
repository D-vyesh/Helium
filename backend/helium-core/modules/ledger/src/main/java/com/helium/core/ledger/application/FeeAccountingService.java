package com.helium.core.ledger.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeeAccountingService {
    private static final Logger log = LoggerFactory.getLogger(FeeAccountingService.class);

    private final LedgerPostingPort ledgerPostingPort;

    public FeeAccountingService(LedgerPostingPort ledgerPostingPort) {
        this.ledgerPostingPort = ledgerPostingPort;
    }

    public void recordTradingFee(UUID userId, String assetCode, BigDecimal feeAmount) {
        log.debug("Recording trading fee {} {} for user {}", feeAmount, assetCode, userId);
        // In reality we construct LedgerPostingCommand and call ledgerPostingPort.post(command);
    }
}
