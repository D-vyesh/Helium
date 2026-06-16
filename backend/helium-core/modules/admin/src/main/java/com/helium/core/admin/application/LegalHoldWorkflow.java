package com.helium.core.admin.application;

import com.helium.core.authuser.domain.Role;
import com.helium.core.wallet.application.AccountFreezeWorkflow;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LegalHoldWorkflow {

    private final GovernanceApprovalService governanceApprovalService;
    private final AccountFreezeWorkflow accountFreezeWorkflow;

    public LegalHoldWorkflow(GovernanceApprovalService governanceApprovalService, AccountFreezeWorkflow accountFreezeWorkflow) {
        this.governanceApprovalService = governanceApprovalService;
        this.accountFreezeWorkflow = accountFreezeWorkflow;
    }

    public void initiateLegalHold(UUID userId, String makerId, String warrantReference) {
        governanceApprovalService.initiateApproval(
            "LEGAL_HOLD_FREEZE", 
            makerId, 
            Role.COMPLIANCE_OFFICER, 
            "{\"userId\":\"" + userId + "\",\"warrant\":\"" + warrantReference + "\"}"
        );
    }

    public void executeLegalHold(UUID userId, String warrantReference) {
        // Gated behind Compliance Officer approval
        accountFreezeWorkflow.freezeAccountForNegativeBalance(userId, "LEGAL HOLD: Warrant " + warrantReference);
    }
}
