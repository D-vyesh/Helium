package com.helium.core.compliance.application;

import java.util.UUID;

/**
 * Interface for checking users or addresses against global sanctions lists (e.g., OFAC).
 */
public interface SanctionsScreeningProvider {

    /**
     * Check if a given user matches any entity on global sanctions lists.
     */
    boolean isUserSanctioned(UUID userId, String fullName, String countryCode);

    /**
     * Check if a given crypto address belongs to a sanctioned entity.
     */
    boolean isAddressSanctioned(String asset, String address);
}
