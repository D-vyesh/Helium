package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "token_confirmation_overrides")
public class TokenConfirmationOverride {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "asset_code", nullable = false, updatable = false, length = 32)
    private String assetCode;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "required_confirmations", nullable = false)
    private int requiredConfirmations;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TokenConfirmationOverride() {}

    public TokenConfirmationOverride(String assetCode, String networkCode, int requiredConfirmations) {
        this.id = UUID.randomUUID();
        this.assetCode = Asset.normalizeCode(assetCode);
        this.networkCode = BlockchainNetwork.normalizeNetworkCode(networkCode);
        this.requiredConfirmations = requiredConfirmations;
    }

    public int requiredConfirmations() { return requiredConfirmations; }
    public String assetCode() { return assetCode; }
    public String networkCode() { return networkCode; }
}
