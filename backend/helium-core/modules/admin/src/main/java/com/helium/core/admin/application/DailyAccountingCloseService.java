package com.helium.core.admin.application;

import com.helium.core.authuser.domain.Role;
import com.helium.core.ledger.application.TreasuryAccountingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Map;

@Service
public class DailyAccountingCloseService {
    private static final Logger log = LoggerFactory.getLogger(DailyAccountingCloseService.class);

    private final TreasuryAccountingService treasuryAccountingService;
    private final GovernanceApprovalService governanceApprovalService;

    public DailyAccountingCloseService(TreasuryAccountingService treasuryAccountingService, GovernanceApprovalService governanceApprovalService) {
        this.treasuryAccountingService = treasuryAccountingService;
        this.governanceApprovalService = governanceApprovalService;
    }

    public void initiateDailyClose(String initiatorId) {
        log.info("Initiating Daily Accounting Close procedure.");
        
        // Step 1: Run reconciliation
        Map<String, Object> navReport = treasuryAccountingService.calculateDailyNav();
        
        // Step 2: Generate attestation string
        String attestationPayload = String.format(
            "{\"nav\": \"%s\", \"assets\": \"%s\", \"liabilities\": \"%s\", \"fees\": \"%s\"}",
            navReport.get("dailyNav"), navReport.get("totalExternalAssets"), 
            navReport.get("totalUserLiabilities"), navReport.get("totalCollectedFees")
        );

        // Step 3: Require dual sign-off
        governanceApprovalService.initiateApproval(
            "DAILY_ACCOUNTING_CLOSE",
            initiatorId,
            Role.COMPLIANCE_OFFICER,
            attestationPayload
        );
        log.info("Daily Close pending COMPLIANCE_OFFICER approval. Attestation: {}", attestationPayload);
    }
}
