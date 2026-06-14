package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "wallet_blockchain_networks")
public class BlockchainNetwork {
    @Id
    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "required_confirmations", nullable = false)
    private int requiredConfirmations;

    @Column(name = "deposit_enabled", nullable = false)
    private boolean depositEnabled;

    @Column(name = "withdrawal_enabled", nullable = false)
    private boolean withdrawalEnabled;

    @Column(name = "minimum_withdrawal", nullable = false, precision = 38, scale = 18)
    private BigDecimal minimumWithdrawal;

    @Column(name = "withdrawal_fee", nullable = false, precision = 38, scale = 18)
    private BigDecimal withdrawalFee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BlockchainNetwork() {
    }

    private BlockchainNetwork(
        String networkCode,
        String assetCode,
        String displayName,
        int requiredConfirmations,
        boolean depositEnabled,
        boolean withdrawalEnabled,
        BigDecimal minimumWithdrawal,
        BigDecimal withdrawalFee,
        Instant now
    ) {
        this.networkCode = normalizeNetworkCode(networkCode);
        this.assetCode = Asset.normalizeCode(assetCode);
        this.displayName = requireText(displayName, "displayName", 120);
        if (requiredConfirmations <= 0) {
            throw new WalletValidationException("required confirmations must be positive");
        }
        this.requiredConfirmations = requiredConfirmations;
        this.depositEnabled = depositEnabled;
        this.withdrawalEnabled = withdrawalEnabled;
        this.minimumWithdrawal = requireNonNegative(minimumWithdrawal, "minimumWithdrawal");
        this.withdrawalFee = requireNonNegative(withdrawalFee, "withdrawalFee");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static BlockchainNetwork register(
        String networkCode,
        String assetCode,
        String displayName,
        int requiredConfirmations,
        boolean depositEnabled,
        boolean withdrawalEnabled,
        BigDecimal minimumWithdrawal,
        BigDecimal withdrawalFee,
        Instant now
    ) {
        return new BlockchainNetwork(
            networkCode,
            assetCode,
            displayName,
            requiredConfirmations,
            depositEnabled,
            withdrawalEnabled,
            minimumWithdrawal,
            withdrawalFee,
            now
        );
    }

    public String networkCode() {
        return networkCode;
    }

    public String assetCode() {
        return assetCode;
    }

    public int requiredConfirmations() {
        return requiredConfirmations;
    }

    public boolean depositEnabled() {
        return depositEnabled;
    }

    public boolean withdrawalEnabled() {
        return withdrawalEnabled;
    }

    public BigDecimal minimumWithdrawal() {
        return minimumWithdrawal;
    }

    public BigDecimal withdrawalFee() {
        return withdrawalFee;
    }

    public static String normalizeNetworkCode(String value) {
        String code = requireText(value, "networkCode", 40).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{2,40}")) {
            throw new WalletValidationException("network code is invalid");
        }
        return code;
    }

    public static BigDecimal requirePositive(BigDecimal value, String field) {
        BigDecimal amount = Objects.requireNonNull(value, field).stripTrailingZeros();
        if (amount.signum() <= 0) {
            throw new WalletValidationException(field + " must be positive");
        }
        return amount;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String field) {
        BigDecimal amount = Objects.requireNonNull(value, field).stripTrailingZeros();
        if (amount.signum() < 0) {
            throw new WalletValidationException(field + " cannot be negative");
        }
        return amount;
    }

    public static String requireText(String value, String field, int maximumLength) {
        String text = Objects.requireNonNull(value, field).trim();
        if (text.isBlank()) {
            throw new WalletValidationException(field + " is required");
        }
        if (text.length() > maximumLength) {
            throw new WalletValidationException(field + " is too long");
        }
        return text;
    }
}
