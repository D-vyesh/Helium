package com.helium.core.wallet.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hot wallet signer for automated, low-value transactions.
 * Keys are kept online in a KMS, but withdrawal limits are heavily enforced.
 */
@Component
public class HotWalletSigner implements CustodyProvider {
    private static final Logger log = LoggerFactory.getLogger(HotWalletSigner.class);

    @Override
    public SigningResult sign(SigningRequest request) {
        throw new UnsupportedOperationException("Hot wallet signing requires a configured KMS/HSM provider");
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public String providerName() {
        return "Helium-HotWallet-KMS";
    }
}
