package com.helium.core.wallet.application;

import com.helium.core.authuser.application.AccountAdministrationPort;
import com.helium.core.authuser.application.SecurityContextData;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AccountFreezeWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AccountFreezeWorkflow.class);
    private final AccountAdministrationPort accountAdministrationPort;

    public AccountFreezeWorkflow(AccountAdministrationPort accountAdministrationPort) {
        this.accountAdministrationPort = accountAdministrationPort;
    }

    public void freezeAccountForNegativeBalance(UUID userId, String reason) {
        log.error("CRITICAL: Freezing account for user {} due to negative balance. Reason: {}", userId, reason);
        accountAdministrationPort.suspend(userId, SecurityContextData.system());
    }
}
