package com.helium.core.wallet.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cold wallet signer for high-value transactions.
 * Requires multi-sig and manual administrative approval. Keys are air-gapped or stored in specialized HSMs.
 */
@Component
public class ColdWalletSigner implements CustodyProvider {
    private static final Logger log = LoggerFactory.getLogger(ColdWalletSigner.class);

    @Override
    public SigningResult sign(SigningRequest request) {
        throw new UnsupportedOperationException("Cold wallet signing requires a configured multi-sig/HSM provider");
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public String providerName() {
        return "Helium-ColdWallet-MultiSig";
    }
}
