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
@Table(name = "wallet_withdrawals")
public class Withdrawal {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_request_id", nullable = false, updatable = false, length = 120)
    private String clientRequestId;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "destination_address", nullable = false, updatable = false, length = 160)
    private String destinationAddress;

    @Column(name = "destination_memo", updatable = false, length = 120)
    private String destinationMemo;

    @Column(name = "amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal fee;

    @Column(name = "reserve_ledger_transaction_id", nullable = false, updatable = false)
    private UUID reserveLedgerTransactionId;

    @Column(name = "settlement_ledger_transaction_id")
    private UUID settlementLedgerTransactionId;

    @Column(name = "release_ledger_transaction_id")
    private UUID releaseLedgerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private WithdrawalStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by", length = 120)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "broadcast_tx_hash", length = 160)
    private String broadcastTxHash;

    @Column(name = "broadcasted_at")
    private Instant broadcastedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Withdrawal() {
    }

    private Withdrawal(
        String clientRequestId,
        String requestHash,
        UUID userId,
        String assetCode,
        String networkCode,
        String destinationAddress,
        String destinationMemo,
        BigDecimal amount,
        BigDecimal fee,
        UUID reserveLedgerTransactionId,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.clientRequestId = BlockchainNetwork.requireText(clientRequestId, "clientRequestId", 120);
        this.requestHash = BlockchainNetwork.requireText(requestHash, "requestHash", 64);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.destinationAddress = BlockchainNetwork.requireText(destinationAddress, "destinationAddress", 160);
        this.destinationMemo = destinationMemo == null || destinationMemo.isBlank()
            ? null
            : BlockchainNetwork.requireText(destinationMemo, "destinationMemo", 120);
        this.amount = BlockchainNetwork.requirePositive(amount, "amount");
        this.fee = BlockchainNetwork.requireNonNegative(fee, "fee");
        this.reserveLedgerTransactionId = Objects.requireNonNull(reserveLedgerTransactionId, "reserveLedgerTransactionId");
        this.status = WithdrawalStatus.REQUESTED;
        this.requestedAt = Objects.requireNonNull(now, "now");
    }

    public static Withdrawal request(
        String clientRequestId,
        String requestHash,
        UUID userId,
        String assetCode,
        String networkCode,
        String destinationAddress,
        String destinationMemo,
        BigDecimal amount,
        BigDecimal fee,
        UUID reserveLedgerTransactionId,
        Instant now
    ) {
        return new Withdrawal(
            clientRequestId,
            requestHash,
            userId,
            assetCode,
            networkCode,
            destinationAddress,
            destinationMemo,
            amount,
            fee,
            reserveLedgerTransactionId,
            now
        );
    }

    public void approve(String actorId, Instant now) {
        requireStatus(WithdrawalStatus.REQUESTED);
        this.status = WithdrawalStatus.APPROVED;
        this.approvedBy = BlockchainNetwork.requireText(actorId, "actorId", 120);
        this.approvedAt = Objects.requireNonNull(now, "now");
    }

    public void reject(String actorId, String reason, UUID releaseLedgerTransactionId, Instant now) {
        requireStatus(WithdrawalStatus.REQUESTED);
        this.status = WithdrawalStatus.REJECTED;
        this.rejectedBy = BlockchainNetwork.requireText(actorId, "actorId", 120);
        this.rejectionReason = BlockchainNetwork.requireText(reason, "reason", 500);
        this.releaseLedgerTransactionId = Objects.requireNonNull(releaseLedgerTransactionId, "releaseLedgerTransactionId");
        this.rejectedAt = Objects.requireNonNull(now, "now");
    }

    public void recordBroadcast(String txHash, Instant now) {
        requireStatus(WithdrawalStatus.APPROVED);
        this.status = WithdrawalStatus.BROADCASTED;
        this.broadcastTxHash = BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.broadcastedAt = Objects.requireNonNull(now, "now");
    }

    public void confirm(UUID settlementLedgerTransactionId, Instant now) {
        requireStatus(WithdrawalStatus.BROADCASTED);
        this.settlementLedgerTransactionId = Objects.requireNonNull(settlementLedgerTransactionId, "settlementLedgerTransactionId");
        this.status = WithdrawalStatus.CONFIRMED;
        this.confirmedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() {
        return id;
    }

    public String clientRequestId() {
        return clientRequestId;
    }

    public String requestHash() {
        return requestHash;
    }

    public UUID userId() {
        return userId;
    }

    public String assetCode() {
        return assetCode;
    }

    public String networkCode() {
        return networkCode;
    }

    public String destinationAddress() {
        return destinationAddress;
    }

    public String destinationMemo() {
        return destinationMemo;
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal fee() {
        return fee;
    }

    public BigDecimal totalDebit() {
        return amount.add(fee).stripTrailingZeros();
    }

    public WithdrawalStatus status() {
        return status;
    }

    public String broadcastTxHash() {
        return broadcastTxHash;
    }

    private void requireStatus(WithdrawalStatus expected) {
        if (status != expected) {
            throw new WalletValidationException("withdrawal must be " + expected + " but was " + status);
        }
    }
}
