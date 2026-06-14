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
@Table(name = "wallet_deposits")
public class Deposit {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "deposit_address_id", nullable = false, updatable = false)
    private UUID depositAddressId;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "tx_hash", nullable = false, updatable = false, length = 160)
    private String txHash;

    @Column(name = "output_index", nullable = false, updatable = false)
    private int outputIndex;

    @Column(name = "amount", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "confirmations", nullable = false)
    private int confirmations;

    @Column(name = "required_confirmations", nullable = false, updatable = false)
    private int requiredConfirmations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DepositStatus status;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Deposit() {
    }

    private Deposit(
        UUID userId,
        UUID depositAddressId,
        String assetCode,
        String networkCode,
        String txHash,
        int outputIndex,
        BigDecimal amount,
        int requiredConfirmations,
        Instant now
    ) {
        if (outputIndex < 0) {
            throw new WalletValidationException("output index cannot be negative");
        }
        if (requiredConfirmations <= 0) {
            throw new WalletValidationException("required confirmations must be positive");
        }
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.depositAddressId = Objects.requireNonNull(depositAddressId, "depositAddressId");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.txHash = BlockchainNetwork.requireText(txHash, "txHash", 160);
        this.outputIndex = outputIndex;
        this.amount = BlockchainNetwork.requirePositive(amount, "amount");
        this.confirmations = 0;
        this.requiredConfirmations = requiredConfirmations;
        this.status = DepositStatus.DETECTED;
        this.detectedAt = Objects.requireNonNull(now, "now");
        this.confirmedAt = null;
    }

    public static Deposit detect(
        UUID userId,
        UUID depositAddressId,
        String assetCode,
        String networkCode,
        String txHash,
        int outputIndex,
        BigDecimal amount,
        int requiredConfirmations,
        Instant now
    ) {
        return new Deposit(
            userId,
            depositAddressId,
            assetCode,
            networkCode,
            txHash,
            outputIndex,
            amount,
            requiredConfirmations,
            now
        );
    }

    public void updateConfirmations(int nextConfirmations, Instant now) {
        if (status == DepositStatus.POSTED || status == DepositStatus.REJECTED) {
            return;
        }
        if (nextConfirmations < confirmations) {
            throw new WalletValidationException("deposit confirmations cannot decrease");
        }
        confirmations = nextConfirmations;
        if (confirmations >= requiredConfirmations && status == DepositStatus.DETECTED) {
            status = DepositStatus.CONFIRMED;
            confirmedAt = Objects.requireNonNull(now, "now");
        }
    }

    public void markPosted(UUID ledgerTransactionId, Instant now) {
        if (status == DepositStatus.POSTED) {
            return;
        }
        if (status != DepositStatus.CONFIRMED) {
            throw new WalletValidationException("only confirmed deposits can be posted");
        }
        this.ledgerTransactionId = Objects.requireNonNull(ledgerTransactionId, "ledgerTransactionId");
        this.status = DepositStatus.POSTED;
        this.postedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() {
        return id;
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

    public String txHash() {
        return txHash;
    }

    public int outputIndex() {
        return outputIndex;
    }

    public BigDecimal amount() {
        return amount;
    }

    public DepositStatus status() {
        return status;
    }

    public UUID ledgerTransactionId() {
        return ledgerTransactionId;
    }
}
