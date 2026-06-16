package com.helium.core.admin.application;

import com.helium.core.authuser.domain.Role;
import com.helium.core.wallet.application.AccountFreezeWorkflow;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EmergencyFreezeService {

    private final GovernanceApprovalService governanceApprovalService;
    private final AccountFreezeWorkflow accountFreezeWorkflow;

    public EmergencyFreezeService(GovernanceApprovalService governanceApprovalService, AccountFreezeWorkflow accountFreezeWorkflow) {
        this.governanceApprovalService = governanceApprovalService;
        this.accountFreezeWorkflow = accountFreezeWorkflow;
    }

    public void proposeEmergencyFreeze(UUID userId, String makerId, String reason) {
        governanceApprovalService.initiateApproval(
            "EMERGENCY_ACCOUNT_FREEZE", 
            makerId, 
            Role.SECURITY_ADMIN, 
            "{\"userId\":\"" + userId + "\",\"reason\":\"" + reason + "\"}"
        );
    }

    public void executeApprovedFreeze(UUID userId, String reason) {
        // Called by GovernanceApprovalService after approval
        accountFreezeWorkflow.freezeAccountForNegativeBalance(userId, "Approved Governance Freeze: " + reason);
    }
}
