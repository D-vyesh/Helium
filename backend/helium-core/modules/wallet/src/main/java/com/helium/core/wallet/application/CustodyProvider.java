package com.helium.core.wallet.application;

/**
 * Provider-independent custody signing boundary. Implementations call KMS,
 * Vault, HSM, MPC, or offline custody systems; HELIUM never receives private keys.
 */
public interface CustodyProvider {
    SigningResult sign(SigningRequest request);

    boolean isHealthy();

    String providerName();
}
