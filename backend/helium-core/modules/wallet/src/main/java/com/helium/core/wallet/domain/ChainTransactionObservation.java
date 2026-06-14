package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_chain_transaction_observations")
public class ChainTransactionObservation {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 40)
    private ChainTransactionDirection direction;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "tx_hash", nullable = false, updatable = false, length = 160)
    private String txHash;

    @Column(name = "output_index", nullable = false, updatable = false)
    private int outputIndex;

    @Column(name = "address", nullable = false, updatable = false, length = 160)
    private String address;

    @Column(name = "memo", updatable = false, length = 120)
    private String memo;

    @Column(name = "amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "confirmations", nullable = false)
    private int confirmations;

    @Column(name = "matched_deposit_id", updatable = false)
    private UUID matchedDepositId;

    @Column(name = "matched_withdrawal_id", updatable = false)
    private UUID matchedWithdrawalId;

    @Column(name = "observed_by", nullable = false, length = 120)
    private String observedBy;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ChainTransactionObservation() {
    }

    private ChainTransactionObservation(
        ChainTransactionDirection direction,
        String assetCode,
        String networkCode,
        String txHash,
        int outputIndex,
        String address,
        String memo,
        BigDecimal amount,
        int confirmations,
        UUID matchedDepositId,
        UUID matchedWithdrawalId,
        String observedBy,
        Instant now
    ) {
        if (outputIndex < 0) {
            throw new WalletValidationException("output index cannot be negative");
        }
        if (confirmations < 0) {
            throw new WalletValidationException("confirmations cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.direction = Objects.requireNonNull(direction, "direction");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.txHash = BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.outputIndex = outputIndex;
        this.address = BlockchainNetwork.requireText(address, "address", 160);
        this.memo = memo == null || memo.isBlank() ? null : BlockchainNetwork.requireText(memo, "memo", 120);
        this.amount = BlockchainNetwork.requirePositive(amount, "amount");
        this.confirmations = confirmations;
        this.matchedDepositId = matchedDepositId;
        this.matchedWithdrawalId = matchedWithdrawalId;
        this.observedBy = BlockchainNetwork.requireText(observedBy, "observedBy", 120);
        this.observedAt = Objects.requireNonNull(now, "now");
    }

    public static ChainTransactionObservation deposit(
        String assetCode,
        String networkCode,
        String txHash,
        int outputIndex,
        String address,
        String memo,
        BigDecimal amount,
        UUID depositId,
        String observedBy,
        Instant now
    ) {
        return new ChainTransactionObservation(
            ChainTransactionDirection.DEPOSIT,
            assetCode,
            networkCode,
            txHash,
            outputIndex,
            address,
            memo,
            amount,
            0,
            depositId,
            null,
            observedBy,
            now
        );
    }

    public static ChainTransactionObservation withdrawal(
        String assetCode,
        String networkCode,
        String txHash,
        String address,
        String memo,
        BigDecimal amount,
        int confirmations,
        UUID withdrawalId,
        String observedBy,
        Instant now
    ) {
        return new ChainTransactionObservation(
            ChainTransactionDirection.WITHDRAWAL,
            assetCode,
            networkCode,
            txHash,
            0,
            address,
            memo,
            amount,
            confirmations,
            null,
            withdrawalId,
            observedBy,
            now
        );
    }

    public void updateConfirmations(int nextConfirmations, String actorId, Instant now) {
        if (nextConfirmations < confirmations) {
            throw new WalletValidationException("chain confirmations cannot decrease");
        }
        confirmations = nextConfirmations;
        observedBy = BlockchainNetwork.requireText(actorId, "actorId", 120);
        observedAt = Objects.requireNonNull(now, "now");
    }

    public void requireSameDepositPayload(String address, BigDecimal amount) {
        String normalizedAddress = BlockchainNetwork.requireText(address, "address", 160);
        BigDecimal normalizedAmount = BlockchainNetwork.requirePositive(amount, "amount");
        if (!this.address.equals(normalizedAddress) || this.amount.compareTo(normalizedAmount) != 0) {
            throw new WalletValidationException("chain transaction replay payload differs");
        }
    }

    public void requireMatchesWithdrawal(Withdrawal withdrawal, int requiredConfirmations) {
        requireMatchesWithdrawalPayload(withdrawal);
        if (confirmations < requiredConfirmations) {
            throw new WalletValidationException("chain observation does not match withdrawal");
        }
    }

    public void requireMatchesWithdrawalPayload(Withdrawal withdrawal) {
        if (direction != ChainTransactionDirection.WITHDRAWAL
            || !Objects.equals(matchedWithdrawalId, withdrawal.id())
            || !assetCode.equals(withdrawal.assetCode())
            || !networkCode.equals(withdrawal.networkCode())
            || !txHash.equals(withdrawal.broadcastTxHash())
            || !address.equals(withdrawal.destinationAddress())
            || !Objects.equals(memo, withdrawal.destinationMemo())
            || amount.compareTo(withdrawal.amount()) != 0) {
            throw new WalletValidationException("chain observation does not match withdrawal");
        }
    }

    public UUID id() {
        return id;
    }

    public int confirmations() {
        return confirmations;
    }
}
