package com.helium.core.app.api.kms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "helium.kms", name = "provider", havingValue = "aws")
public class AwsKmsProvider implements KmsProvider {
    private static final Logger log = LoggerFactory.getLogger(AwsKmsProvider.class);

    @Override
    public byte[] encrypt(byte[] plaintext) {
        throw new UnsupportedOperationException("AWS KMS encryption is not configured");
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        throw new UnsupportedOperationException("AWS KMS decryption is not configured");
    }

    @Override
    public String name() {
        return "AWS KMS";
    }
}
