package com.helium.core.admin.application;

import com.helium.core.wallet.application.WalletLedgerAccounts;

import com.helium.core.authuser.domain.Role;
import com.helium.core.admin.application.GovernanceApprovalService;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TreasuryManagementService {
    private static final Logger log = LoggerFactory.getLogger(TreasuryManagementService.class);

    private final GovernanceApprovalService governanceApprovalService;
    private final WalletLedgerAccounts ledgerAccounts;

    public TreasuryManagementService(GovernanceApprovalService governanceApprovalService, WalletLedgerAccounts ledgerAccounts) {
        this.governanceApprovalService = governanceApprovalService;
        this.ledgerAccounts = ledgerAccounts;
    }

    public void monitorWalletFragmentation(String assetCode) {
        // Mocking the query to the active hot/cold wallets 
        // In reality, this would query the CustodyProvider for Hot and Cold balances
        BigDecimal hotWalletBalance = new BigDecimal("150000.00");
        BigDecimal coldWalletBalance = new BigDecimal("850000.00");
        BigDecimal totalReserves = hotWalletBalance.add(coldWalletBalance);

        // Fetch User Liabilities
        // This relies on the new query added to BalanceSnapshotRepository in PoR
        BigDecimal totalUserLiabilities = new BigDecimal("950000.00");

        if (totalReserves.compareTo(totalUserLiabilities) < 0) {
            log.error("CRITICAL ALARM: Fractional Reserve Detected! Liabilities ({}) > Reserves ({})", 
                totalUserLiabilities, totalReserves);
            // Trigger emergency procedures
        }

        BigDecimal hotWalletRatio = hotWalletBalance.divide(totalReserves, 4, java.math.RoundingMode.HALF_UP);
        
        // If hot wallet falls below 10%, we need to sweep from Cold
        if (hotWalletRatio.compareTo(new BigDecimal("0.10")) < 0) {
            log.warn("Hot wallet ratio ({}%) is below 10% target. Initiating Cold Sweep Approval.", 
                hotWalletRatio.multiply(new BigDecimal("100")));
            
            // Initiate a Maker/Checker request for the sweep
            governanceApprovalService.initiateApproval(
                "COLD_TO_HOT_SWEEP", 
                "system-monitor", 
                Role.TREASURY_ADMIN, 
                "{\"asset\":\"" + assetCode + "\",\"amount\":\"50000.00\"}"
            );
        }
    }
}
