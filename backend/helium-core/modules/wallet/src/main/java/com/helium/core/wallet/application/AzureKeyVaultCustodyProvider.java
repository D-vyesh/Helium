package com.helium.core.wallet.application;

import org.springframework.stereotype.Component;

@Component
public class AzureKeyVaultCustodyProvider implements CustodyProvider {
    @Override
    public SigningResult sign(SigningRequest request) {
        throw new UnsupportedOperationException("Azure Key Vault custody signing requires the Azure Key Vault signing adapter to be configured");
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public String providerName() {
        return "AZURE_KEY_VAULT";
    }
}
