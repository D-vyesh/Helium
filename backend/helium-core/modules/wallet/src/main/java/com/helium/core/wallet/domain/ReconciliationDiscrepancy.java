package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallet_reconciliation_discrepancies")
public class ReconciliationDiscrepancy {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, updatable = false, length = 60)
    private ReconciliationDiscrepancyType discrepancyType;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "wallet_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal walletTotal;

    @Column(name = "ledger_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal ledgerTotal;

    @Column(name = "chain_total", nullable = false, updatable = false, precision = 38, scale = 18)
    private BigDecimal chainTotal;

    @Column(name = "details", nullable = false, updatable = false, length = 500)
    private String details;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected ReconciliationDiscrepancy() {
    }

    private ReconciliationDiscrepancy(
        ReconciliationDiscrepancyType discrepancyType,
        String assetCode,
        String networkCode,
        BigDecimal walletTotal,
        BigDecimal ledgerTotal,
        BigDecimal chainTotal,
        String details,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.discrepancyType = Objects.requireNonNull(discrepancyType, "discrepancyType");
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.walletTotal = Objects.requireNonNull(walletTotal, "walletTotal").stripTrailingZeros();
        this.ledgerTotal = Objects.requireNonNull(ledgerTotal, "ledgerTotal").stripTrailingZeros();
        this.chainTotal = Objects.requireNonNull(chainTotal, "chainTotal").stripTrailingZeros();
        this.details = BlockchainNetwork.requireText(details, "details", 500);
        this.detectedAt = Objects.requireNonNull(now, "now");
    }

    public static ReconciliationDiscrepancy record(
        ReconciliationDiscrepancyType discrepancyType,
        String assetCode,
        String networkCode,
        BigDecimal walletTotal,
        BigDecimal ledgerTotal,
        BigDecimal chainTotal,
        String details,
        Instant now
    ) {
        return new ReconciliationDiscrepancy(
            discrepancyType,
            assetCode,
            networkCode,
            walletTotal,
            ledgerTotal,
            chainTotal,
            details,
            now
        );
    }
}

