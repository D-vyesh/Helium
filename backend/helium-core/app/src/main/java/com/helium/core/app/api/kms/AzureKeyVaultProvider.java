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
        log.debug("Encrypting payload via Azure Key Vault");
        // Azure Key Vault integration stub
        return plaintext;
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        log.debug("Decrypting payload via Azure Key Vault");
        // Azure Key Vault integration stub
        return ciphertext;
    }

    @Override
    public String name() {
        return "Azure Key Vault";
    }
}
