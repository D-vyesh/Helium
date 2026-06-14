package com.helium.core.wallet.application;

public interface WalletReconciliationPort {
    void updateChainMonitor(UpdateChainMonitorCommand command);

    ReconciliationSnapshot snapshot(String assetCode, String networkCode);
}
