package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "wallet_assets")
public class Asset {
    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "scale", nullable = false)
    private int scale;

    @Column(name = "deposit_enabled", nullable = false)
    private boolean depositEnabled;

    @Column(name = "withdrawal_enabled", nullable = false)
    private boolean withdrawalEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Asset() {
    }

    private Asset(String code, String name, int scale, boolean depositEnabled, boolean withdrawalEnabled, Instant now) {
        this.code = normalizeCode(code);
        this.name = requireText(name, "name", 120);
        if (scale < 0 || scale > 18) {
            throw new WalletValidationException("asset scale must be between 0 and 18");
        }
        this.scale = scale;
        this.depositEnabled = depositEnabled;
        this.withdrawalEnabled = withdrawalEnabled;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static Asset register(String code, String name, int scale, boolean depositEnabled, boolean withdrawalEnabled, Instant now) {
        return new Asset(code, name, scale, depositEnabled, withdrawalEnabled, now);
    }

    public void updatePolicy(boolean depositEnabled, boolean withdrawalEnabled, Instant now) {
        this.depositEnabled = depositEnabled;
        this.withdrawalEnabled = withdrawalEnabled;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public String code() {
        return code;
    }

    public int scale() {
        return scale;
    }

    public boolean depositEnabled() {
        return depositEnabled;
    }

    public boolean withdrawalEnabled() {
        return withdrawalEnabled;
    }

    public static String normalizeCode(String value) {
        String code = requireText(value, "assetCode", 32).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{2,32}")) {
            throw new WalletValidationException("asset code is invalid");
        }
        return code;
    }

    private static String requireText(String value, String field, int maximumLength) {
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

