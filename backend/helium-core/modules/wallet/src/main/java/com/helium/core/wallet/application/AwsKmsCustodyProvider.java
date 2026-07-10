package com.helium.core.wallet.application;

import org.springframework.stereotype.Component;

@Component
public class AwsKmsCustodyProvider implements CustodyProvider {
    @Override
    public SigningResult sign(SigningRequest request) {
        throw new UnsupportedOperationException("AWS KMS custody signing requires the AWS KMS asymmetric signing adapter to be configured");
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public String providerName() {
        return "AWS_KMS";
    }
}
