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
        log.debug("Encrypting payload via GCP KMS");
        // GCP KMS integration stub
        return plaintext;
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        log.debug("Decrypting payload via GCP KMS");
        // GCP KMS integration stub
        return ciphertext;
    }

    @Override
    public String name() {
        return "GCP KMS";
    }
}
