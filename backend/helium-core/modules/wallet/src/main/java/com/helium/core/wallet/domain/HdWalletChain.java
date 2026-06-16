package com.helium.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "hd_wallet_chains")
public class HdWalletChain {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "network_code", nullable = false, updatable = false, length = 40)
    private String networkCode;

    @Column(name = "xpub", nullable = false, updatable = false, length = 255)
    private String xpub;

    @Column(name = "derivation_path", nullable = false, updatable = false, length = 255)
    private String derivationPath;

    @Column(name = "current_index", nullable = false)
    private int currentIndex;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected HdWalletChain() {}

    public HdWalletChain(String networkCode, String xpub, String derivationPath) {
        this.id = UUID.randomUUID();
        this.networkCode = networkCode;
        this.xpub = xpub;
        this.derivationPath = derivationPath;
        this.currentIndex = 0;
    }

    public int allocateNextIndex() {
        return currentIndex++;
    }

    public UUID id() { return id; }
    public String networkCode() { return networkCode; }
    public String xpub() { return xpub; }
    public String derivationPath() { return derivationPath; }
    public int currentIndex() { return currentIndex; }
}
