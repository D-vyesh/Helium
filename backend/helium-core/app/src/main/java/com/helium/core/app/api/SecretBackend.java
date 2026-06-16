package com.helium.core.app.api;

import java.util.Map;

/**
 * Abstraction over secret storage backends (Vault, AWS KMS, environment variables).
 * Implementations must be thread-safe as they are invoked from scheduled refresh tasks.
 */
public interface SecretBackend {

    /**
     * Fetch secrets from the backend at the given logical path.
     *
     * @param path logical path such as "helium/api-keys" or "helium/database"
     * @return map of secret key to secret value
     */
    Map<String, String> fetchSecrets(String path);

    /**
     * @return human-readable name of this backend for logging
     */
    String name();
}
