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
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.domain.Withdrawal;
import com.helium.core.wallet.infrastructure.AssetRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.WithdrawalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalRequestService implements WithdrawalRequestPort {
    private final WithdrawalRepository withdrawalRepository;
    private final AssetRepository assetRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final AccountStatusPort accountStatusPort;
    private final WalletActorService actorService;
    private final WalletIdempotencyLockService lockService;
    private final WalletLedgerAccounts ledgerAccounts;
    private final LedgerPostingPort ledgerPostingPort;
    private final WalletAuditService auditService;
    private final Clock clock;

    public WithdrawalRequestService(
        WithdrawalRepository withdrawalRepository,
        AssetRepository assetRepository,
        BlockchainNetworkRepository networkRepository,
        AccountStatusPort accountStatusPort,
        WalletActorService actorService,
        WalletIdempotencyLockService lockService,
        WalletLedgerAccounts ledgerAccounts,
        LedgerPostingPort ledgerPostingPort,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.withdrawalRepository = withdrawalRepository;
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
    public WithdrawalView requestWithdrawal(RequestWithdrawalCommand command) {
        UUID userId = actorService.requireCurrentUserId();
        String actorId = userId.toString();
        String clientRequestId = BlockchainNetwork.requireText(command.clientRequestId(), "clientRequestId", 120);
        lockService.lock("wallet:withdrawal-request", userId + ":" + clientRequestId);
        Asset asset = assetRepository.findById(Asset.normalizeCode(command.assetCode()))
            .orElseThrow(() -> new WalletValidationException("asset is not registered"));
        BlockchainNetwork network = networkRepository.findById(BlockchainNetwork.normalizeNetworkCode(command.networkCode()))
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        if (!network.assetCode().equals(asset.code())) {
            throw new WalletValidationException("network does not support asset");
        }
        if (!asset.withdrawalEnabled() || !network.withdrawalEnabled()) {
            throw new WalletValidationException("withdrawals are disabled");
        }
        BigDecimal amount = BlockchainNetwork.requirePositive(command.amount(), "amount");
        if (amount.compareTo(network.minimumWithdrawal()) < 0) {
            throw new WalletValidationException("withdrawal amount is below network minimum");
        }
        String requestHash = WalletHash.withdrawalRequestHash(
            userId,
            asset.code(),
            network.networkCode(),
            command.destinationAddress(),
            command.destinationMemo(),
            amount,
            network.withdrawalFee()
        );

        Withdrawal existing = withdrawalRepository.findByUserIdAndClientRequestId(userId, clientRequestId).orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new WalletValidationException("withdrawal replay payload differs");
            }
            return WithdrawalApprovalService.toView(existing);
        }
        requireActiveUser(userId);

        LedgerPostingResult reserve = reserveFunds(command, userId, actorId, asset, network, amount);
        Withdrawal withdrawal = withdrawalRepository.save(Withdrawal.request(
            clientRequestId,
            requestHash,
            userId,
            asset.code(),
            network.networkCode(),
            command.destinationAddress(),
            command.destinationMemo(),
            amount,
            network.withdrawalFee(),
            reserve.transactionId(),
            clock.instant()
        ));
        auditService.record(WalletAuditEventType.WITHDRAWAL_REQUESTED, withdrawal.id(), actorId, clientRequestId);
        return WithdrawalApprovalService.toView(withdrawal);
    }

    private void requireActiveUser(UUID userId) {
        UserAccountStatus status = accountStatusPort.statusOf(userId);
        if (status != UserAccountStatus.ACTIVE) {
            throw new WalletValidationException("user account is not active: " + status);
        }
    }

    private LedgerPostingResult reserveFunds(
        RequestWithdrawalCommand command,
        UUID userId,
        String actorId,
        Asset asset,
        BlockchainNetwork network,
        BigDecimal amount
    ) {
        LedgerAccountView available = ledgerAccounts.userAvailable(userId.toString(), asset.code());
        LedgerAccountView locked = ledgerAccounts.userLocked(userId.toString(), asset.code());
        BigDecimal total = amount.add(network.withdrawalFee()).stripTrailingZeros();
        String businessReference = "wallet:withdrawal-reserve:" + userId + ":" + command.clientRequestId();
        return ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.WITHDRAWAL,
            businessReference,
            businessReference,
            "reserve wallet withdrawal funds",
            AuditMetadata.of(actorId, "wallet", businessReference, command.clientRequestId(), "withdrawal requested"),
            List.of(
                new PostingLineCommand(available.accountId(), PostingDirection.DEBIT, total),
                new PostingLineCommand(locked.accountId(), PostingDirection.CREDIT, total)
            )
        ));
    }
}
