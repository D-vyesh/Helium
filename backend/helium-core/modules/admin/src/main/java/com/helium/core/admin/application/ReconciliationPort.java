package com.helium.core.admin.application;

import java.time.LocalDate;

public interface ReconciliationPort {
    ReconciliationSummary runDailyReconciliation(LocalDate businessDate);

    ReconciliationReportView reconcileLedgerVsWallet(String assetCode, String networkCode, LocalDate businessDate);

    ReconciliationReportView reconcileWalletVsChain(String assetCode, String networkCode, LocalDate businessDate);

    ReconciliationReportView reconcileTradingSettlement(String marketSymbol, LocalDate businessDate);

    ReconciliationReportView reconcileMatchingExecution(String marketSymbol, LocalDate businessDate);

    ReconciliationSummary captureDailyBalanceSnapshots(LocalDate businessDate);

    String exportReportsCsv(LocalDate businessDate);
}
