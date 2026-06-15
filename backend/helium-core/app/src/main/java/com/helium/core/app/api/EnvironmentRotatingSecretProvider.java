package com.helium.core.app.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentRotatingSecretProvider implements RotatingSecretProvider {
    private final ApiKeyProperties properties;

    public EnvironmentRotatingSecretProvider(ApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    public SecretVersion current() {
        return new SecretVersion(properties.currentVersion(), properties.currentPepper());
    }

    @Override
    public Optional<SecretVersion> find(String version) {
        return verificationVersions().stream()
            .filter(secret -> secret.version().equals(version))
            .findFirst();
    }

    @Override
    public List<SecretVersion> verificationVersions() {
        List<SecretVersion> versions = new ArrayList<>();
        versions.add(current());
        if (properties.previousVersion() != null
            && !properties.previousVersion().isBlank()
            && properties.previousPepper() != null
            && !properties.previousPepper().isBlank()) {
            versions.add(new SecretVersion(properties.previousVersion(), properties.previousPepper()));
        }
        return List.copyOf(versions);
    }
}
