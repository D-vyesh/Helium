package com.helium.core.wallet.application;

import com.helium.core.ledger.application.BalanceQueryPort;
import com.helium.core.ledger.application.LedgerAccountView;
import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.ChainMonitorState;
import com.helium.core.wallet.domain.ChainTransactionDirection;
import com.helium.core.wallet.domain.DepositStatus;
import com.helium.core.wallet.domain.ReconciliationDiscrepancy;
import com.helium.core.wallet.domain.ReconciliationDiscrepancyType;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.WithdrawalStatus;
import com.helium.core.wallet.infrastructure.AssetRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.ChainMonitorStateRepository;
import com.helium.core.wallet.infrastructure.ChainTransactionObservationRepository;
import com.helium.core.wallet.infrastructure.DepositRepository;
import com.helium.core.wallet.infrastructure.ReconciliationDiscrepancyRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletReconciliationService implements WalletReconciliationPort {
    private final ChainMonitorStateRepository chainMonitorStateRepository;
    private final DepositRepository depositRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final AssetRepository assetRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final ChainTransactionObservationRepository observationRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final WalletLedgerAccounts ledgerAccounts;
    private final BalanceQueryPort balanceQueryPort;
    private final WalletActorService actorService;
    private final WalletAuditService auditService;
    private final Clock clock;

    public WalletReconciliationService(
        ChainMonitorStateRepository chainMonitorStateRepository,
        DepositRepository depositRepository,
        WithdrawalRepository withdrawalRepository,
        AssetRepository assetRepository,
        BlockchainNetworkRepository networkRepository,
        ChainTransactionObservationRepository observationRepository,
        ReconciliationDiscrepancyRepository discrepancyRepository,
        WalletLedgerAccounts ledgerAccounts,
        BalanceQueryPort balanceQueryPort,
        WalletActorService actorService,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.chainMonitorStateRepository = chainMonitorStateRepository;
        this.depositRepository = depositRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.assetRepository = assetRepository;
        this.networkRepository = networkRepository;
        this.observationRepository = observationRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.ledgerAccounts = ledgerAccounts;
        this.balanceQueryPort = balanceQueryPort;
        this.actorService = actorService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void updateChainMonitor(UpdateChainMonitorCommand command) {
        String actorId = actorService.requireChainMonitorActor();
        String networkCode = BlockchainNetwork.normalizeNetworkCode(command.networkCode());
        if (!networkRepository.existsById(networkCode)) {
            throw new WalletValidationException("network is not registered");
        }
        ChainMonitorState state = chainMonitorStateRepository.findByNetworkCodeForUpdate(networkCode)
            .orElseGet(() -> chainMonitorStateRepository.save(ChainMonitorState.start(networkCode, command.blockHeight(), clock.instant())));
        state.advanceTo(command.blockHeight(), clock.instant());
        auditService.record(WalletAuditEventType.CHAIN_MONITOR_UPDATED, null, actorId, networkCode + ":" + command.blockHeight());
    }

    @Override
    @Transactional
    public ReconciliationSnapshot snapshot(String assetCode, String networkCode) {
        String actorId = actorService.requireOperationsActor();
        String normalizedAsset = Asset.normalizeCode(assetCode);
        String normalizedNetwork = BlockchainNetwork.normalizeNetworkCode(networkCode);
        if (!assetRepository.existsById(normalizedAsset)) {
            throw new WalletValidationException("asset is not registered");
        }
        BlockchainNetwork network = networkRepository.findById(normalizedNetwork)
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        if (!network.assetCode().equals(normalizedAsset)) {
            throw new WalletValidationException("network does not support asset");
        }

        BigDecimal walletTotal = walletTotal(normalizedAsset, normalizedNetwork);
        BigDecimal ledgerTotal = ledgerTotal(normalizedAsset, normalizedNetwork);
        BigDecimal chainTotal = chainTotal(normalizedAsset, normalizedNetwork, network.requiredConfirmations());
        long discrepancies = recordDiscrepancies(normalizedAsset, normalizedNetwork, walletTotal, ledgerTotal, chainTotal);

        auditService.record(WalletAuditEventType.RECONCILIATION_CHECKED, null, actorId, normalizedAsset + ":" + normalizedNetwork);
        return new ReconciliationSnapshot(normalizedAsset, normalizedNetwork, walletTotal, ledgerTotal, chainTotal, discrepancies);
    }

    private BigDecimal walletTotal(String assetCode, String networkCode) {
        BigDecimal deposits = depositRepository.sumAmountByAssetCodeAndNetworkCodeAndStatus(
            assetCode,
            networkCode,
            DepositStatus.POSTED
        );
        BigDecimal withdrawals = withdrawalRepository.sumAmountByAssetCodeAndNetworkCodeAndStatus(
            assetCode,
            networkCode,
            WithdrawalStatus.CONFIRMED
        );
        return zeroIfNull(deposits).subtract(zeroIfNull(withdrawals)).stripTrailingZeros();
    }

    private BigDecimal ledgerTotal(String assetCode, String networkCode) {
        LedgerAccountView external = ledgerAccounts.external(networkCode, assetCode);
        return balanceQueryPort.getBalance(external.accountId()).currentBalance().stripTrailingZeros();
    }

    private BigDecimal chainTotal(String assetCode, String networkCode, int requiredConfirmations) {
        BigDecimal deposits = observationRepository.sumConfirmedAmount(
            assetCode,
            networkCode,
            ChainTransactionDirection.DEPOSIT,
            requiredConfirmations
        );
        BigDecimal withdrawals = observationRepository.sumConfirmedAmount(
            assetCode,
            networkCode,
            ChainTransactionDirection.WITHDRAWAL,
            requiredConfirmations
        );
        return zeroIfNull(deposits).subtract(zeroIfNull(withdrawals)).stripTrailingZeros();
    }

    private long recordDiscrepancies(
        String assetCode,
        String networkCode,
        BigDecimal walletTotal,
        BigDecimal ledgerTotal,
        BigDecimal chainTotal
    ) {
        long count = 0;
        if (walletTotal.compareTo(ledgerTotal) != 0) {
            discrepancyRepository.save(ReconciliationDiscrepancy.record(
                ReconciliationDiscrepancyType.WALLET_LEDGER_MISMATCH,
                assetCode,
                networkCode,
                walletTotal,
                ledgerTotal,
                chainTotal,
                "wallet total does not match ledger external balance",
                clock.instant()
            ));
            count++;
        }
        if (chainTotal.compareTo(walletTotal) != 0) {
            discrepancyRepository.save(ReconciliationDiscrepancy.record(
                ReconciliationDiscrepancyType.CHAIN_WALLET_MISMATCH,
                assetCode,
                networkCode,
                walletTotal,
                ledgerTotal,
                chainTotal,
                "confirmed chain total does not match wallet posted total",
                clock.instant()
            ));
            count++;
        }
        return count;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
