package com.helium.core.app.api.kms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "helium.kms", name = "provider", havingValue = "azure")
public class AzureKeyVaultProvider implements KmsProvider {
    private static final Logger log = LoggerFactory.getLogger(AzureKeyVaultProvider.class);

    @Override
    public byte[] encrypt(byte[] plaintext) {
        throw new UnsupportedOperationException("Azure Key Vault encryption is not configured");
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        throw new UnsupportedOperationException("Azure Key Vault decryption is not configured");
    }

    @Override
    public String name() {
        return "Azure Key Vault";
    }
}
