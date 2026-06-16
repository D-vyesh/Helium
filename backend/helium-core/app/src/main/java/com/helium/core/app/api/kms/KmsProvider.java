package com.helium.core.app.api.kms;

/**
 * Cloud KMS provider interface for envelope encryption and dynamic secrets.
 */
public interface KmsProvider {
    /**
     * Encrypt a plaintext payload.
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * Decrypt a ciphertext payload.
     */
    byte[] decrypt(byte[] ciphertext);

    /**
     * @return the provider name
     */
    String name();
}
