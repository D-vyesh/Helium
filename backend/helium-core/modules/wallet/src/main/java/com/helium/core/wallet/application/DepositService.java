package com.helium.core.wallet.application;

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
import com.helium.core.wallet.domain.ChainTransactionObservation;
import com.helium.core.wallet.domain.Deposit;
import com.helium.core.wallet.domain.DepositAddress;
import com.helium.core.wallet.domain.DepositStatus;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.AssetRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.ChainTransactionObservationRepository;
import com.helium.core.wallet.infrastructure.DepositAddressRepository;
import com.helium.core.wallet.infrastructure.DepositRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositService implements DepositPort {
    private final DepositRepository depositRepository;
    private final DepositAddressRepository addressRepository;
    private final AssetRepository assetRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final ChainTransactionObservationRepository observationRepository;
    private final WalletActorService actorService;
    private final WalletIdempotencyLockService lockService;
    private final WalletLedgerAccounts ledgerAccounts;
    private final LedgerPostingPort ledgerPostingPort;
    private final WalletAuditService auditService;
    private final Clock clock;

    public DepositService(
        DepositRepository depositRepository,
        DepositAddressRepository addressRepository,
        AssetRepository assetRepository,
        BlockchainNetworkRepository networkRepository,
        ChainTransactionObservationRepository observationRepository,
        WalletActorService actorService,
        WalletIdempotencyLockService lockService,
        WalletLedgerAccounts ledgerAccounts,
        LedgerPostingPort ledgerPostingPort,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.depositRepository = depositRepository;
        this.addressRepository = addressRepository;
        this.assetRepository = assetRepository;
        this.networkRepository = networkRepository;
        this.observationRepository = observationRepository;
        this.actorService = actorService;
        this.lockService = lockService;
        this.ledgerAccounts = ledgerAccounts;
        this.ledgerPostingPort = ledgerPostingPort;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DepositView detectDeposit(DetectDepositCommand command) {
        String actorId = actorService.requireChainMonitorActor();
        String networkCode = BlockchainNetwork.normalizeNetworkCode(command.networkCode());
        String txHash = BlockchainNetwork.requireText(command.txHash(), "txHash", 160);
        lockService.lock("wallet:deposit", networkCode + ":" + txHash + ":" + command.outputIndex());

        ChainTransactionObservation existingObservation = observationRepository
            .findByChainReferenceForUpdate(networkCode, txHash, command.outputIndex())
            .orElse(null);
        if (existingObservation != null) {
            existingObservation.requireSameDepositPayload(command.address(), command.amount());
            return depositRepository.findByNetworkCodeAndTxHashAndOutputIndex(networkCode, txHash, command.outputIndex())
                .map(DepositService::toView)
                .orElseThrow(() -> new WalletValidationException("chain observation is not linked to a deposit"));
        }

        Deposit existing = depositRepository
            .findByNetworkCodeAndTxHashAndOutputIndex(networkCode, txHash, command.outputIndex())
            .orElse(null);
        if (existing != null) {
            return toView(existing);
        }

        DepositAddress address = addressRepository.findByAddressAndNetworkCode(command.address(), networkCode)
            .orElseThrow(() -> new WalletValidationException("deposit address is not assigned"));
        Asset asset = assetRepository.findById(address.assetCode())
            .orElseThrow(() -> new WalletValidationException("asset is not registered"));
        BlockchainNetwork network = networkRepository.findById(networkCode)
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        if (!asset.depositEnabled() || !network.depositEnabled()) {
            throw new WalletValidationException("deposits are disabled");
        }

        Deposit deposit = depositRepository.save(Deposit.detect(
            address.userId(),
            address.id(),
            address.assetCode(),
            network.networkCode(),
            txHash,
            command.outputIndex(),
            command.amount(),
            network.requiredConfirmations(),
            clock.instant()
        ));
        observationRepository.save(ChainTransactionObservation.deposit(
            address.assetCode(),
            network.networkCode(),
            txHash,
            command.outputIndex(),
            address.address(),
            address.memo(),
            command.amount(),
            deposit.id(),
            actorId,
            clock.instant()
        ));
        auditService.record(WalletAuditEventType.DEPOSIT_DETECTED, deposit.id(), actorId, deposit.txHash());
        postIfConfirmed(deposit, actorId);
        return toView(deposit);
    }

    @Override
    @Transactional
    public DepositView updateConfirmations(UpdateDepositConfirmationsCommand command) {
        String actorId = actorService.requireChainMonitorActor();
        Deposit deposit = depositRepository.findByIdForUpdate(command.depositId())
            .orElseThrow(() -> new WalletValidationException("deposit was not found"));
        ChainTransactionObservation observation = observationRepository.findByMatchedDepositId(deposit.id())
            .orElseThrow(() -> new WalletValidationException("deposit has no chain observation"));
        observation.updateConfirmations(command.confirmations(), actorId, clock.instant());
        deposit.updateConfirmations(command.confirmations(), clock.instant());
        auditService.record(
            WalletAuditEventType.DEPOSIT_CONFIRMATIONS_UPDATED,
            deposit.id(),
            actorId,
            Integer.toString(command.confirmations())
        );
        postIfConfirmed(deposit, actorId);
        return toView(deposit);
    }

    private void postIfConfirmed(Deposit deposit, String actorId) {
        if (deposit.status() != DepositStatus.CONFIRMED) {
            return;
        }
        LedgerAccountView external = ledgerAccounts.external(deposit.networkCode(), deposit.assetCode());
        LedgerAccountView userAvailable = ledgerAccounts.userAvailable(deposit.userId().toString(), deposit.assetCode());
        String businessReference = "wallet:deposit:" + deposit.id();
        LedgerPostingResult result = ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            businessReference,
            businessReference,
            "confirmed wallet deposit",
            AuditMetadata.of(actorId, "wallet", businessReference, deposit.txHash(), "deposit confirmed"),
            List.of(
                new PostingLineCommand(external.accountId(), PostingDirection.DEBIT, deposit.amount()),
                new PostingLineCommand(userAvailable.accountId(), PostingDirection.CREDIT, deposit.amount())
            )
        ));
        deposit.markPosted(result.transactionId(), clock.instant());
        auditService.record(WalletAuditEventType.DEPOSIT_POSTED, deposit.id(), actorId, result.transactionId().toString());
    }

    static DepositView toView(Deposit deposit) {
        return new DepositView(
            deposit.id(),
            deposit.userId(),
            deposit.assetCode(),
            deposit.networkCode(),
            deposit.amount(),
            deposit.status(),
            deposit.ledgerTransactionId()
        );
    }
}
