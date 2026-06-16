package com.helium.core.wallet.infrastructure.blockchain;

import com.helium.core.wallet.domain.Deposit;
import com.helium.core.wallet.domain.Withdrawal;
import java.util.List;
import java.util.UUID;

/**
 * Common abstraction for all integrated blockchain networks.
 */
public interface BlockchainProvider {

    /**
     * Identifies the network this provider supports (e.g., "BTC", "ETH", "SOL").
     */
    String networkId();

    /**
     * Generate a new deposit address for the given user/asset.
     */
    String generateAddress(String asset, UUID userId);

    /**
     * Scan a range of blocks for incoming deposits matching registered addresses.
     * @param startBlock the block height to start scanning from (inclusive)
     * @param endBlock the block height to end scanning at (inclusive)
     * @return A list of newly detected deposits
     */
    List<DetectedDeposit> scanForDeposits(long startBlock, long endBlock);

    /**
     * Prepare, sign, and build a raw transaction for a withdrawal.
     */
    byte[] buildAndSignWithdrawal(Withdrawal withdrawal);

    /**
     * Broadcast a signed transaction to the network.
     * @param signedTx raw bytes of the transaction
     * @return the transaction hash/signature on success
     */
    String broadcastTransaction(byte[] signedTx);

    /**
     * Check the current number of confirmations for a given transaction hash.
     * @return number of confirmations, or -1 if the transaction is dropped/reorged
     */
    long getConfirmations(String txHash);

    /**
     * Fetch the current highest block number synced by the node.
     */
    long getLatestBlockHeight();
}
