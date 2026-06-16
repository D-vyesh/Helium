package com.helium.core.compliance.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StubSanctionsScreeningProvider implements SanctionsScreeningProvider {
    private static final Logger log = LoggerFactory.getLogger(StubSanctionsScreeningProvider.class);

    @Override
    public boolean isUserSanctioned(UUID userId, String fullName, String countryCode) {
        log.debug("Screening user {} against OFAC sanctions stub.", userId);
        return false;
    }

    @Override
    public boolean isAddressSanctioned(String asset, String address) {
        log.debug("Screening address {} for asset {} against sanctions lists stub.", address, asset);
        return false;
    }
}
