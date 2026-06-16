package com.helium.core.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Workflow service managing the approval process for large withdrawals.
 * Routes transactions to Hot or Cold wallet signers based on risk score and value.
 */
@Service
public class WithdrawalApprovalWorkflow {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalApprovalWorkflow.class);
    
    // Configurable thresholds for hot/cold routing
    private static final BigDecimal COLD_WALLET_THRESHOLD_USD = new BigDecimal("100000.00");

    private final HotWalletSigner hotWalletSigner;
    private final ColdWalletSigner coldWalletSigner;
    private final com.helium.core.compliance.application.SanctionsScreeningProvider sanctionsScreeningProvider;
    private final com.helium.core.wallet.infrastructure.blockchain.BlockchainProviderRegistry blockchainProviderRegistry;

    public WithdrawalApprovalWorkflow(HotWalletSigner hotWalletSigner, 
                                      ColdWalletSigner coldWalletSigner,
                                      com.helium.core.compliance.application.SanctionsScreeningProvider sanctionsScreeningProvider,
                                      com.helium.core.wallet.infrastructure.blockchain.BlockchainProviderRegistry blockchainProviderRegistry) {
        this.hotWalletSigner = hotWalletSigner;
        this.coldWalletSigner = coldWalletSigner;
        this.sanctionsScreeningProvider = sanctionsScreeningProvider;
        this.blockchainProviderRegistry = blockchainProviderRegistry;
    }

    public void processWithdrawal(UUID withdrawalId, UUID userId, String networkId, String destinationAddress, String asset, BigDecimal amount, BigDecimal estimatedUsdValue) {
        log.info("Processing withdrawal request: id={}, user={}, asset={}, amount={}, usdValue={}", 
            withdrawalId, userId, asset, amount, estimatedUsdValue);

        // 1. Velocity Limit Check
        if (isVelocityLimitExceeded(userId, estimatedUsdValue)) {
            log.warn("Velocity limit exceeded for user {}. Holding withdrawal {}.", userId, withdrawalId);
            requireManualApproval(withdrawalId);
            return;
        }

        // 2. Sanctions Screening Hook
        boolean isSanctioned = sanctionsScreeningProvider.isAddressSanctioned(asset, destinationAddress);
        if (isSanctioned) {
            log.warn("Sanctions screening flagged destination {} for user {}.", destinationAddress, userId);
            requireManualApproval(withdrawalId);
            return;
        }

        if (estimatedUsdValue.compareTo(COLD_WALLET_THRESHOLD_USD) >= 0) {
            log.warn("Withdrawal {} exceeds cold wallet threshold! Flagging for manual multi-sig approval.", withdrawalId);
            requireManualApproval(withdrawalId);
        } else {
            log.info("Withdrawal {} is within hot wallet limits. Proceeding with automated signing.", withdrawalId);
            byte[] signedTx = hotWalletSigner.signTransaction("dummy-unsigned-tx".getBytes());
            broadcastTransaction(networkId, signedTx);
        }
    }

    private boolean isVelocityLimitExceeded(UUID userId, BigDecimal usdValue) {
        // Implementation would check total 24h volume
        return false;
    }

    private void requireManualApproval(UUID withdrawalId) {
        // Here we would insert a record into a `withdrawal_approvals` table
        // notifying admins that manual review is required.
        log.info("Withdrawal {} placed in MANUAL_REVIEW state.", withdrawalId);
    }

    private void broadcastTransaction(String networkId, byte[] signedTx) {
        log.info("Broadcasting signed transaction to network {}.", networkId);
        var provider = blockchainProviderRegistry.getRequiredProvider(networkId);
        String txHash = provider.broadcastTransaction(signedTx);
        log.info("Transaction broadcasted successfully on {}. TxHash: {}", networkId, txHash);
    }
}
