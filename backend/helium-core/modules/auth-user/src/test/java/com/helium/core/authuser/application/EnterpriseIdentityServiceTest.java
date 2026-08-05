package com.helium.core.authuser.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.helium.core.authuser.domain.AuthValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnterpriseIdentityServiceTest {

    private final EnterpriseIdentityService service = new EnterpriseIdentityService();

    @Test
    void refusesUnconfiguredSamlAndWebauthnFlows() {
        assertThrows(AuthValidationException.class,
            () -> service.configureSsoForOrganization(UUID.randomUUID(), "https://idp.example/metadata"));
        assertThrows(AuthValidationException.class,
            () -> service.requireStepUpAuthentication(UUID.randomUUID(), "withdrawal approval"));
    }
}
