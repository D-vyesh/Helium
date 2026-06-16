package com.helium.core.app.api;

import java.util.List;
import java.util.Optional;

/**
 * Provides versioned secrets that support rotation without restart.
 * Implementations may back onto environment variables, Vault, AWS KMS, etc.
 */
public interface RotatingSecretProvider {
    SecretVersion current();

    Optional<SecretVersion> find(String version);

    List<SecretVersion> verificationVersions();

    record SecretVersion(String version, String value) {}
}
