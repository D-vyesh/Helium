package com.helium.core.wallet.application;

import com.helium.core.authuser.application.AccountStatusPort;
import com.helium.core.authuser.domain.UserAccountStatus;
import com.helium.core.ledger.application.LedgerAccountView;
import com.helium.core.ledger.application.LedgerPostingCommand;
import com.helium.core.ledger.application.LedgerPostingPort;
import com.helium.core.ledger.application.LedgerPostingResult;
import com.helium.core.ledger.application.PostingLineCommand;
import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.PostingDirection;
import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.BroadcastAttempt;
import com.helium.core.wallet.domain.ChainTransactionObservation;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.domain.WithdrawalStatus;
import com.helium.core.wallet.infrastructure.AssetRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.BroadcastAttemptRepository;
import com.helium.core.wallet.infrastructure.ChainTransactionObservationRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalApprovalService implements WithdrawalApprovalPort {
    private final WithdrawalRepository withdrawalRepository;
    private final BroadcastAttemptRepository broadcastAttemptRepository;
    private final ChainTransactionObservationRepository observationRepository;
    private final AssetRepository assetRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final AccountStatusPort accountStatusPort;
    private final WalletActorService actorService;
    private final WalletIdempotencyLockService lockService;
    private final WalletLedgerAccounts ledgerAccounts;
    private final LedgerPostingPort ledgerPostingPort;
    private final WalletAuditService auditService;
    private final java.time.Clock clock;

    public WithdrawalApprovalService(
        WithdrawalRepository withdrawalRepository,
        BroadcastAttemptRepository broadcastAttemptRepository,
        ChainTransactionObservationRepository observationRepository,
        AssetRepository assetRepository,
        BlockchainNetworkRepository networkRepository,
        AccountStatusPort accountStatusPort,
        WalletActorService actorService,
        WalletIdempotencyLockService lockService,
        WalletLedgerAccounts ledgerAccounts,
        LedgerPostingPort ledgerPostingPort,
        WalletAuditService auditService,
        java.time.Clock clock
    ) {
        this.withdrawalRepository = withdrawalRepository;
        this.broadcastAttemptRepository = broadcastAttemptRepository;
        this.observationRepository = observationRepository;
        this.assetRepository = assetRepository;
        this.networkRepository = networkRepository;
        this.accountStatusPort = accountStatusPort;
        this.actorService = actorService;
        this.lockService = lockService;
        this.ledgerAccounts = ledgerAccounts;
        this.ledgerPostingPort = ledgerPostingPort;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WithdrawalView approve(ApproveWithdrawalCommand command) {
        String actorId = actorService.requireOperationsActor();
        Withdrawal withdrawal = withdrawalForUpdate(command.withdrawalId());
        requireActiveWithdrawalUser(withdrawal);
        requirePayoutPolicy(withdrawal);
        withdrawal.approve(actorId, clock.instant());
        auditService.record(WalletAuditEventType.WITHDRAWAL_APPROVED, withdrawal.id(), actorId, withdrawal.clientRequestId());
        return toView(withdrawal);
    }

    @Override
    @Transactional
    public WithdrawalView reject(RejectWithdrawalCommand command) {
        String actorId = actorService.requireOperationsActor();
        Withdrawal withdrawal = withdrawalForUpdate(command.withdrawalId());
        requireActiveWithdrawalUser(withdrawal);
        LedgerPostingResult release = releaseFunds(withdrawal, actorId);
        withdrawal.reject(actorId, command.reason(), release.transactionId(), clock.instant());
        auditService.record(WalletAuditEventType.WITHDRAWAL_REJECTED, withdrawal.id(), actorId, command.reason());
        return toView(withdrawal);
    }

    @Override
    @Transactional
    public WithdrawalView recordBroadcast(RecordBroadcastCommand command) {
        String actorId = actorService.requireOperationsActor();
        Withdrawal withdrawal = withdrawalForUpdate(command.withdrawalId());
        requireActiveWithdrawalUser(withdrawal);
        requirePayoutPolicy(withdrawal);
        int attemptNumber = broadcastAttemptRepository.countByWithdrawalId(withdrawal.id()) + 1;
        broadcastAttemptRepository.save(BroadcastAttempt.recorded(
            withdrawal.id(),
            attemptNumber,
            command.txHash(),
            actorId,
            clock.instant()
        ));
        withdrawal.recordBroadcast(command.txHash(), clock.instant());
        auditService.record(WalletAuditEventType.WITHDRAWAL_BROADCAST_RECORDED, withdrawal.id(), actorId, command.txHash());
        return toView(withdrawal);
    }

    @Override
    @Transactional
    public void observeWithdrawal(ObserveWithdrawalCommand command) {
        String actorId = actorService.requireChainMonitorActor();
        Withdrawal withdrawal = withdrawalForUpdate(command.withdrawalId());
        String txHash = BlockchainNetwork.requireText(command.txHash(), "txHash", 160);
        lockService.lock("wallet:withdrawal-observe", withdrawal.networkCode() + ":" + txHash + ":0");
        ChainTransactionObservation observation = observationRepository.findByMatchedWithdrawalId(withdrawal.id())
            .orElseGet(() -> observationRepository
                .findByChainReferenceForUpdate(withdrawal.networkCode(), txHash, 0)
                .orElseGet(() -> observationRepository.save(ChainTransactionObservation.withdrawal(
                    withdrawal.assetCode(),
                    withdrawal.networkCode(),
                    txHash,
                    command.destinationAddress(),
                    command.destinationMemo(),
                    command.amount(),
                    command.confirmations(),
                    withdrawal.id(),
                    actorId,
                    clock.instant()
                ))));
        observation.updateConfirmations(command.confirmations(), actorId, clock.instant());
        observation.requireMatchesWithdrawalPayload(withdrawal);
    }

    @Override
    @Transactional
    public WithdrawalView confirm(ConfirmWithdrawalCommand command) {
        String actorId = actorService.requireChainMonitorActor();
        Withdrawal withdrawal = withdrawalForUpdate(command.withdrawalId());
        if (withdrawal.status() == WithdrawalStatus.CONFIRMED) {
            return toView(withdrawal);
        }

        BlockchainNetwork network = networkRepository.findById(withdrawal.networkCode())
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        lockService.lock("wallet:withdrawal-confirm", withdrawal.id().toString());
        ChainTransactionObservation observation = observationRepository.findByMatchedWithdrawalId(withdrawal.id())
            .orElseThrow(() -> new WalletValidationException("withdrawal chain observation was not found"));
        observation.requireMatchesWithdrawal(withdrawal, network.requiredConfirmations());

        LedgerPostingResult settlement = settleFunds(withdrawal, actorId);
        withdrawal.confirm(settlement.transactionId(), clock.instant());
        auditService.record(WalletAuditEventType.WITHDRAWAL_CONFIRMED, withdrawal.id(), actorId, withdrawal.broadcastTxHash());
        return toView(withdrawal);
    }

    private Withdrawal withdrawalForUpdate(UUID withdrawalId) {
        return withdrawalRepository.findByIdForUpdate(withdrawalId)
            .orElseThrow(() -> new WalletValidationException("withdrawal was not found"));
    }

    private void requireActiveWithdrawalUser(Withdrawal withdrawal) {
        UserAccountStatus status = accountStatusPort.statusOf(withdrawal.userId());
        if (status != UserAccountStatus.ACTIVE) {
            throw new WalletValidationException("withdrawal user account is not active: " + status);
        }
    }

    private void requirePayoutPolicy(Withdrawal withdrawal) {
        Asset asset = assetRepository.findById(withdrawal.assetCode())
            .orElseThrow(() -> new WalletValidationException("asset is not registered"));
        BlockchainNetwork network = networkRepository.findById(withdrawal.networkCode())
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        if (!network.assetCode().equals(asset.code())) {
            throw new WalletValidationException("network does not support asset");
        }
        if (!asset.withdrawalEnabled() || !network.withdrawalEnabled()) {
            throw new WalletValidationException("withdrawals are disabled");
        }
    }

    private LedgerPostingResult releaseFunds(Withdrawal withdrawal, String actorId) {
        LedgerAccountView locked = ledgerAccounts.userLocked(withdrawal.userId().toString(), withdrawal.assetCode());
        LedgerAccountView available = ledgerAccounts.userAvailable(withdrawal.userId().toString(), withdrawal.assetCode());
        String businessReference = "wallet:withdrawal-release:" + withdrawal.id();
        return ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.WITHDRAWAL,
            businessReference,
            businessReference,
            "release rejected wallet withdrawal funds",
            AuditMetadata.of(actorId, "wallet", businessReference, withdrawal.clientRequestId(), "withdrawal rejected"),
            List.of(
                new PostingLineCommand(locked.accountId(), PostingDirection.DEBIT, withdrawal.totalDebit()),
                new PostingLineCommand(available.accountId(), PostingDirection.CREDIT, withdrawal.totalDebit())
            )
        ));
    }

    private LedgerPostingResult settleFunds(Withdrawal withdrawal, String actorId) {
        LedgerAccountView locked = ledgerAccounts.userLocked(withdrawal.userId().toString(), withdrawal.assetCode());
        LedgerAccountView external = ledgerAccounts.external(withdrawal.networkCode(), withdrawal.assetCode());
        List<PostingLineCommand> lines = new ArrayList<>();
        lines.add(new PostingLineCommand(locked.accountId(), PostingDirection.DEBIT, withdrawal.totalDebit()));
        lines.add(new PostingLineCommand(external.accountId(), PostingDirection.CREDIT, withdrawal.amount()));
        if (withdrawal.fee().signum() > 0) {
            LedgerAccountView fee = ledgerAccounts.fee(withdrawal.assetCode());
            lines.add(new PostingLineCommand(fee.accountId(), PostingDirection.CREDIT, withdrawal.fee()));
        }
        String businessReference = "wallet:withdrawal-settle:" + withdrawal.id();
        return ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.WITHDRAWAL,
            businessReference,
            businessReference,
            "settle confirmed wallet withdrawal",
            AuditMetadata.of(actorId, "wallet", businessReference, withdrawal.clientRequestId(), "withdrawal confirmed"),
            lines
        ));
    }

    static WithdrawalView toView(Withdrawal withdrawal) {
        return new WithdrawalView(
            withdrawal.id(),
            withdrawal.clientRequestId(),
            withdrawal.userId(),
            withdrawal.assetCode(),
            withdrawal.networkCode(),
            withdrawal.amount(),
            withdrawal.fee(),
            withdrawal.status(),
            withdrawal.broadcastTxHash()
        );
    }
}
