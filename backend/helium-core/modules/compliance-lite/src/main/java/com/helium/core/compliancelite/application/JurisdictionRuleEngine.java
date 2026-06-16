package com.helium.core.compliancelite.application;

import com.helium.core.compliancelite.domain.Jurisdiction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class JurisdictionRuleEngine {
    private static final Logger log = LoggerFactory.getLogger(JurisdictionRuleEngine.class);

    private static final Set<String> PRIVACY_COINS = Set.of("XMR", "ZEC", "DASH");

    public boolean canTradeAsset(Jurisdiction jurisdiction, String assetCode) {
        if (jurisdiction.restrictsPrivacyCoins() && PRIVACY_COINS.contains(assetCode)) {
            log.warn("GEO-BLOCK: Jurisdiction {} restricts trading of privacy coin {}", jurisdiction, assetCode);
            return false;
        }
        // In a real system, we'd check a database for explicit geo-blocking rules
        return true;
    }

    public void enforceWithdrawalLimit(Jurisdiction jurisdiction, java.math.BigDecimal amountUsd) {
        if (amountUsd.compareTo(jurisdiction.defaultWithdrawalLimitUsd()) > 0) {
            log.warn("COMPLIANCE ALERT: Withdrawal amount {} exceeds {} limit of {} for jurisdiction {}",
                amountUsd, jurisdiction.regulatorName(), jurisdiction.defaultWithdrawalLimitUsd(), jurisdiction);
            throw new SecurityException("Withdrawal exceeds jurisdictional limits. Manual review required.");
        }
    }
}
