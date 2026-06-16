package com.helium.core.wallet.application;

import java.util.UUID;

/**
 * Abstraction for custody and key management (KMS/HSM/Threshold Signatures).
 */
public interface CustodyProvider {
    
    /**
     * Generate a new deposit address for the given asset and user.
     */
    String generateAddress(String asset, UUID userId);

    /**
     * Sign a transaction payload securely.
     * @param unsignedTx The raw transaction to sign
     * @return The signed transaction ready for broadcast
     */
    byte[] signTransaction(byte[] unsignedTx);

    /**
     * @return true if this provider is currently available and healthy
     */
    boolean isHealthy();

    /**
     * @return The identifier of the custody implementation
     */
    String getProviderName();
}
