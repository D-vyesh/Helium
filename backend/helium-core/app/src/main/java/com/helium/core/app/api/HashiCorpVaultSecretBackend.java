package com.helium.core.app.api;

import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

@Component
@ConditionalOnProperty(prefix = "helium.secrets", name = "backend", havingValue = "vault")
public class HashiCorpVaultSecretBackend implements SecretBackend {

    private static final Logger log = LoggerFactory.getLogger(HashiCorpVaultSecretBackend.class);
    private final VaultTemplate vaultTemplate;

    public HashiCorpVaultSecretBackend(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    @Override
    public Map<String, String> fetchSecrets(String path) {
        log.debug("Fetching secrets from HashiCorp Vault at path: {}", path);
        try {
            VaultResponse response = vaultTemplate.read("secret/data/" + path);
            if (response != null && response.getData() != null) {
                Object data = response.getData().get("data");
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> secrets = (Map<String, String>) data;
                    return secrets;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch secrets from HashiCorp Vault: {}", e.getMessage(), e);
        }
        return Collections.emptyMap();
    }

    @Override
    public String name() {
        return "HashiCorpVault";
    }
}
