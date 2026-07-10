package com.helium.core.app.api.kms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "helium.kms", name = "provider", havingValue = "gcp")
public class GcpKmsProvider implements KmsProvider {
    private static final Logger log = LoggerFactory.getLogger(GcpKmsProvider.class);

    @Override
    public byte[] encrypt(byte[] plaintext) {
        throw new UnsupportedOperationException("Google Cloud KMS encryption is not configured");
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        throw new UnsupportedOperationException("Google Cloud KMS decryption is not configured");
    }

    @Override
    public String name() {
        return "GCP KMS";
    }
}
