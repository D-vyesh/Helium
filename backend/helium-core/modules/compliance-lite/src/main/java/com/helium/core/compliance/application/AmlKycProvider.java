package com.helium.core.compliance.application;

import java.util.UUID;

/**
 * Interface for integrating with third-party AML (Anti-Money Laundering) 
 * and KYC (Know Your Customer) providers (e.g., Chainalysis, Elliptic, SumSub).
 */
public interface AmlKycProvider {
    
    /**
     * Perform KYC verification for a user based on their submitted documents/data.
     */
    KycResult verifyIdentity(UUID userId, Object kycData);
    
    /**
     * Score an incoming crypto deposit for AML risk.
     */
    AmlRiskScore evaluateDepositRisk(String txHash, String address);

    record KycResult(boolean approved, String referenceId, String rejectionReason) {}
    record AmlRiskScore(int riskScore, String riskCategory, boolean isHighRisk) {}
}
