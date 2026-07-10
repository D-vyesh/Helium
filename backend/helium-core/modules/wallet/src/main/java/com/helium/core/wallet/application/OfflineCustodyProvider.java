package com.helium.core.wallet.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OfflineCustodyProvider implements CustodyProvider {
    private final Path responseDirectory;

    public OfflineCustodyProvider(@Value("${helium.wallet.custody.offline.response-directory:}") String responseDirectory) {
        this.responseDirectory = responseDirectory == null || responseDirectory.isBlank()
            ? null
            : Path.of(responseDirectory);
    }

    @Override
    public SigningResult sign(SigningRequest request) {
        if (!isHealthy()) {
            throw new UnsupportedOperationException("Offline custody response directory is not configured");
        }
        Instant start = Instant.now();
        Path responseFile = responseDirectory.resolve(request.withdrawalId() + ".signature");
        try {
            if (!Files.isRegularFile(responseFile)) {
                throw new IllegalStateException("offline signature response is not available for withdrawal " + request.withdrawalId());
            }
            String signature = Files.readString(responseFile, StandardCharsets.UTF_8).trim();
            if (signature.isBlank()) {
                throw new IllegalStateException("offline signature response is empty");
            }
            return new SigningResult(providerName(), signature, signature, Duration.between(start, Instant.now()));
        } catch (Exception exception) {
            throw new IllegalStateException("Offline custody signing failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean isHealthy() {
        return responseDirectory != null && Files.isDirectory(responseDirectory);
    }

    @Override
    public String providerName() {
        return "OFFLINE";
    }
}
