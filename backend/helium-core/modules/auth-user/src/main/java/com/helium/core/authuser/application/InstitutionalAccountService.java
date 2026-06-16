package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class InstitutionalAccountService {
    private static final Logger log = LoggerFactory.getLogger(InstitutionalAccountService.class);

    private final Map<UUID, Organization> orgStore = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> whitelistedAddresses = new ConcurrentHashMap<>();

    public Organization createCorporateAccount(String name) {
        Organization org = new Organization(name);
        orgStore.put(org.id(), org);
        log.info("Created new institutional Organization: {} ({})", name, org.id());
        return org;
    }

    public void addWhitelistedWithdrawalAddress(UUID orgId, String address) {
        if (!orgStore.containsKey(orgId)) {
            throw new IllegalArgumentException("Organization not found");
        }
        whitelistedAddresses.computeIfAbsent(orgId, k -> ConcurrentHashMap.newKeySet()).add(address);
        log.info("Added whitelisted withdrawal address {} to Organization {}", address, orgId);
    }

    public boolean isAddressWhitelisted(UUID orgId, String address) {
        Set<String> addresses = whitelistedAddresses.get(orgId);
        return addresses != null && addresses.contains(address);
    }
}
