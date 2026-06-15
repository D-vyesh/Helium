package com.helium.core.app.api;

import java.util.List;
import java.util.Optional;

public interface RotatingSecretProvider {
    SecretVersion current();

    Optional<SecretVersion> find(String version);

    List<SecretVersion> verificationVersions();

    record SecretVersion(String version, String value) {}
}
