package com.helium.core.wallet.application;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.helium.core.authuser.application.AccountAdministrationPort;
import com.helium.core.authuser.application.SecurityContextData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountFreezeWorkflowTest {

    @Test
    void suspendsTheAccountThroughTheAuthBoundary() {
        AccountAdministrationPort accountAdministrationPort = mock(AccountAdministrationPort.class);
        AccountFreezeWorkflow workflow = new AccountFreezeWorkflow(accountAdministrationPort);
        UUID userId = UUID.randomUUID();

        workflow.freezeAccountForNegativeBalance(userId, "negative wallet balance");

        verify(accountAdministrationPort).suspend(eq(userId), eq(SecurityContextData.system()));
    }
}
