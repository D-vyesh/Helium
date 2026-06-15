package com.helium.core.admin.application;

import com.helium.core.admin.domain.AdminAuditAction;
import com.helium.core.admin.domain.DailyBalanceSnapshot;
import com.helium.core.admin.domain.DiscrepancySeverity;
import com.helium.core.admin.domain.ReconciliationDiscrepancyRecord;
import com.helium.core.admin.domain.ReconciliationReport;
import com.helium.core.admin.domain.ReconciliationReportStatus;
import com.helium.core.admin.domain.ReconciliationReportType;
import com.helium.core.admin.infrastructure.DailyBalanceSnapshotRepository;
import com.helium.core.admin.infrastructure.ReconciliationDiscrepancyRecordRepository;
import com.helium.core.admin.infrastructure.ReconciliationReportRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService implements ReconciliationPort {
    private final AdminSecurityService securityService;
    private final AdminAuditService auditService;
    private final ReconciliationReportRepository reportRepository;
    private final ReconciliationDiscrepancyRecordRepository discrepancyRepository;
    private final DailyBalanceSnapshotRepository balanceSnapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ReconciliationService(
        AdminSecurityService securityService,
        AdminAuditService auditService,
        ReconciliationReportRepository reportRepository,
        ReconciliationDiscrepancyRecordRepository discrepancyRepository,
        DailyBalanceSnapshotRepository balanceSnapshotRepository,
        JdbcTemplate jdbcTemplate,
        Clock clock
    ) {
        this.securityService = securityService;
        this.auditService = auditService;
        this.reportRepository = reportRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReconciliationSummary runDailyReconciliation(LocalDate businessDate) {
        String actorId = securityService.requireAdminActor();
        List<String[]> scopes = walletScopes();
        List<ReconciliationReportView> reports = new ArrayList<>();
        for (String[] scope : scopes) {
            reports.add(createLedgerWalletReport(scope[0], scope[1], businessDate, actorId));
            reports.add(createWalletChainReport(scope[0], scope[1], businessDate, actorId));
        }
        for (String market : markets()) {
            reports.add(createTradingSettlementReport(market, businessDate, actorId));
            reports.add(createMatchingExecutionReport(market, businessDate, actorId));
        }
        reports.addAll(captureDailyBalanceSnapshotsInternal(businessDate, actorId));
        return new ReconciliationSummary(reports, reports.stream().filter(view -> view.status() == ReconciliationReportStatus.DISCREPANCY).count());
    }

    @Override
    @Transactional
    public ReconciliationReportView reconcileLedgerVsWallet(String assetCode, String networkCode, LocalDate businessDate) {
        return createLedgerWalletReport(assetCode, networkCode, businessDate, securityService.requireAdminActor());
    }

    @Override
    @Transactional
    public ReconciliationReportView reconcileWalletVsChain(String assetCode, String networkCode, LocalDate businessDate) {
        return createWalletChainReport(assetCode, networkCode, businessDate, securityService.requireAdminActor());
    }

    @Override
    @Transactional
    public ReconciliationReportView reconcileTradingSettlement(String marketSymbol, LocalDate businessDate) {
        return createTradingSettlementReport(marketSymbol, businessDate, securityService.requireAdminActor());
    }

    @Override
    @Transactional
    public ReconciliationReportView reconcileMatchingExecution(String marketSymbol, LocalDate businessDate) {
        return createMatchingExecutionReport(marketSymbol, businessDate, securityService.requireAdminActor());
    }

    @Override
    @Transactional
    public ReconciliationSummary captureDailyBalanceSnapshots(LocalDate businessDate) {
        List<ReconciliationReportView> views = captureDailyBalanceSnapshotsInternal(businessDate, securityService.requireAdminActor());
        return new ReconciliationSummary(views, views.stream().filter(view -> view.status() == ReconciliationReportStatus.DISCREPANCY).count());
    }

    @Override
    @Transactional(readOnly = true)
    public String exportReportsCsv(LocalDate businessDate) {
        String actorId = securityService.requireAdminActor();
        auditService.record(AdminAuditAction.CSV_EXPORTED, actorId, "RECONCILIATION", businessDate.toString(), "reconciliation reports exported");
        StringBuilder csv = new StringBuilder("reportId,type,status,scope,difference\n");
        for (ReconciliationReport report : reportRepository.findByBusinessDateOrderByCreatedAtDesc(businessDate)) {
            csv.append(report.id()).append(',')
                .append(report.reportType()).append(',')
                .append(report.status()).append(',')
                .append(report.scopeKey()).append(',')
                .append(report.difference()).append('\n');
        }
        return csv.toString();
    }

    @Scheduled(cron = "${helium.admin.reconciliation.daily-cron:0 15 0 * * *}")
    @Transactional
    public void scheduledDailyBalanceSnapshot() {
        String actorId = "system:admin-reconciliation";
        captureDailyBalanceSnapshotsInternal(LocalDate.now(clock), actorId);
    }

    private ReconciliationReportView createLedgerWalletReport(String assetCode, String networkCode, LocalDate businessDate, String actorId) {
        String asset = normalize(assetCode);
        String network = normalize(networkCode);
        BigDecimal ledgerTotal = ledgerExternalTotal(asset, network);
        BigDecimal walletTotal = walletTotal(asset, network);
        return saveReport(
            ReconciliationReportType.LEDGER_WALLET,
            businessDate,
            asset + ":" + network,
            "ledger_external",
            "wallet_posted",
            ledgerTotal,
            walletTotal,
            actorId,
            "ledger external balance differs from wallet posted total"
        );
    }

    private ReconciliationReportView createWalletChainReport(String assetCode, String networkCode, LocalDate businessDate, String actorId) {
        String asset = normalize(assetCode);
        String network = normalize(networkCode);
        BigDecimal walletTotal = walletTotal(asset, network);
        BigDecimal chainTotal = chainTotal(asset, network);
        return saveReport(
            ReconciliationReportType.WALLET_CHAIN,
            businessDate,
            asset + ":" + network,
            "wallet_posted",
            "chain_confirmed",
            walletTotal,
            chainTotal,
            actorId,
            "wallet posted total differs from confirmed chain total"
        );
    }

    private ReconciliationReportView createTradingSettlementReport(String marketSymbol, LocalDate businessDate, String actorId) {
        String market = marketSymbol.trim().toUpperCase();
        BigDecimal settledNotional = queryDecimal(
            "select coalesce(sum(quantity * price), 0) from trading_settlement_instructions where market_symbol = ? and status = 'SETTLED'",
            market
        );
        BigDecimal ledgerTradeTransactions = queryDecimal(
            "select coalesce(count(*), 0) from trading_settlement_instructions where market_symbol = ? and status = 'SETTLED' and ledger_transaction_id is not null",
            market
        );
        return saveReport(
            ReconciliationReportType.TRADING_SETTLEMENT,
            businessDate,
            market,
            "settled_notional",
            "ledger_settlement_count",
            settledNotional,
            ledgerTradeTransactions,
            actorId,
            "trading settled notional requires operator review against ledger settlement count"
        );
    }

    private ReconciliationReportView createMatchingExecutionReport(String marketSymbol, LocalDate businessDate, String actorId) {
        String market = marketSymbol.trim().toUpperCase();
        BigDecimal matchingExecutions = queryDecimal(
            "select coalesce(count(*), 0) from matching_executions where market_symbol = ?",
            market
        );
        BigDecimal tradingSettlements = queryDecimal(
            "select coalesce(count(distinct execution_id), 0) from trading_settlement_instructions where market_symbol = ?",
            market
        );
        return saveReport(
            ReconciliationReportType.MATCHING_EXECUTION,
            businessDate,
            market,
            "matching_executions",
            "trading_settlements",
            matchingExecutions,
            tradingSettlements,
            actorId,
            "matching execution count differs from trading settlement count"
        );
    }

    private List<ReconciliationReportView> captureDailyBalanceSnapshotsInternal(LocalDate businessDate, String actorId) {
        List<ReconciliationReportView> views = new ArrayList<>();
        for (String asset : assets()) {
            BigDecimal ledgerTotal = ledgerAssetTotal(asset);
            BigDecimal walletTotal = walletAssetTotal(asset);
            BigDecimal tradingLockedTotal = tradingLockedTotal(asset);
            balanceSnapshotRepository.save(DailyBalanceSnapshot.capture(
                businessDate,
                asset,
                ledgerTotal,
                walletTotal,
                tradingLockedTotal,
                actorId,
                clock.instant()
            ));
            views.add(saveReport(
                ReconciliationReportType.DAILY_BALANCE,
                businessDate,
                asset,
                "ledger_total",
                "wallet_plus_trading_locked",
                ledgerTotal,
                walletTotal.add(tradingLockedTotal),
                actorId,
                "daily asset balance snapshot differs"
            ));
        }
        return views;
    }

    private ReconciliationReportView saveReport(
        ReconciliationReportType type,
        LocalDate businessDate,
        String scopeKey,
        String leftLabel,
        String rightLabel,
        BigDecimal leftTotal,
        BigDecimal rightTotal,
        String actorId,
        String discrepancyDetails
    ) {
        ReconciliationReport report = reportRepository.save(ReconciliationReport.compare(
            type,
            businessDate,
            scopeKey,
            leftLabel,
            rightLabel,
            leftTotal,
            rightTotal,
            actorId,
            clock.instant()
        ));
        if (report.status() == ReconciliationReportStatus.DISCREPANCY) {
            discrepancyRepository.save(ReconciliationDiscrepancyRecord.record(
                report.id(),
                type,
                severityFor(report.difference()),
                scopeKey,
                leftTotal,
                rightTotal,
                discrepancyDetails,
                clock.instant()
            ));
        }
        auditService.record(AdminAuditAction.RECONCILIATION_REPORT_CREATED, actorId, "RECONCILIATION_REPORT", report.id().toString(), type + ":" + scopeKey);
        return new ReconciliationReportView(report.id(), report.reportType(), report.status(), report.scopeKey(), report.difference());
    }

    private DiscrepancySeverity severityFor(BigDecimal difference) {
        BigDecimal absolute = difference.abs();
        if (absolute.compareTo(new BigDecimal("100000")) >= 0) {
            return DiscrepancySeverity.CRITICAL;
        }
        if (absolute.compareTo(new BigDecimal("1000")) >= 0) {
            return DiscrepancySeverity.HIGH;
        }
        if (absolute.compareTo(BigDecimal.ONE) >= 0) {
            return DiscrepancySeverity.MEDIUM;
        }
        return DiscrepancySeverity.LOW;
    }

    private BigDecimal ledgerExternalTotal(String assetCode, String networkCode) {
        return queryDecimal(
            """
            select coalesce(sum(snapshot.current_balance), 0)
            from ledger_balance_snapshots snapshot
            join ledger_accounts account on account.id = snapshot.account_id
            where account.asset_code = ?
              and account.owner_type = 'EXTERNAL'
              and account.owner_id = ?
            """,
            assetCode,
            "chain:" + networkCode + ":" + assetCode
        );
    }

    private BigDecimal walletTotal(String assetCode, String networkCode) {
        BigDecimal deposits = queryDecimal(
            "select coalesce(sum(amount), 0) from wallet_deposits where asset_code = ? and network_code = ? and status = 'POSTED'",
            assetCode,
            networkCode
        );
        BigDecimal withdrawals = queryDecimal(
            "select coalesce(sum(amount), 0) from wallet_withdrawals where asset_code = ? and network_code = ? and status = 'CONFIRMED'",
            assetCode,
            networkCode
        );
        return deposits.subtract(withdrawals).stripTrailingZeros();
    }

    private BigDecimal chainTotal(String assetCode, String networkCode) {
        Integer confirmations = jdbcTemplate.queryForObject(
            "select required_confirmations from wallet_blockchain_networks where asset_code = ? and network_code = ?",
            Integer.class,
            assetCode,
            networkCode
        );
        BigDecimal deposits = queryDecimal(
            """
            select coalesce(sum(amount), 0)
            from wallet_chain_transaction_observations
            where asset_code = ? and network_code = ? and direction = 'DEPOSIT' and confirmations >= ?
            """,
            assetCode,
            networkCode,
            confirmations
        );
        BigDecimal withdrawals = queryDecimal(
            """
            select coalesce(sum(amount), 0)
            from wallet_chain_transaction_observations
            where asset_code = ? and network_code = ? and direction = 'WITHDRAWAL' and confirmations >= ?
            """,
            assetCode,
            networkCode,
            confirmations
        );
        return deposits.subtract(withdrawals).stripTrailingZeros();
    }

    private BigDecimal ledgerAssetTotal(String assetCode) {
        return queryDecimal(
            """
            select coalesce(sum(snapshot.current_balance), 0)
            from ledger_balance_snapshots snapshot
            join ledger_accounts account on account.id = snapshot.account_id
            where account.asset_code = ?
            """,
            assetCode
        );
    }

    private BigDecimal walletAssetTotal(String assetCode) {
        BigDecimal deposits = queryDecimal("select coalesce(sum(amount), 0) from wallet_deposits where asset_code = ? and status = 'POSTED'", assetCode);
        BigDecimal withdrawals = queryDecimal("select coalesce(sum(amount), 0) from wallet_withdrawals where asset_code = ? and status = 'CONFIRMED'", assetCode);
        return deposits.subtract(withdrawals).stripTrailingZeros();
    }

    private BigDecimal tradingLockedTotal(String assetCode) {
        return queryDecimal(
            "select coalesce(sum(reserved_amount), 0) from trading_order_reservations where asset_code = ? and status = 'ACTIVE'",
            assetCode
        );
    }

    private List<String[]> walletScopes() {
        return jdbcTemplate.query(
            "select asset_code, network_code from wallet_blockchain_networks order by asset_code, network_code",
            (rs, rowNum) -> new String[] {rs.getString("asset_code"), rs.getString("network_code")}
        );
    }

    private List<String> markets() {
        return jdbcTemplate.query("select symbol from trading_markets order by symbol", (rs, rowNum) -> rs.getString("symbol"));
    }

    private List<String> assets() {
        return jdbcTemplate.query("select code from wallet_assets order by code", (rs, rowNum) -> rs.getString("code"));
    }

    private BigDecimal queryDecimal(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
