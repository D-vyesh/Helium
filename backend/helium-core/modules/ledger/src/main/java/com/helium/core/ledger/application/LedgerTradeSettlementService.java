package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.PostingDirection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerTradeSettlementService implements TradeSettlementPort {
    private final LedgerAccountPort ledgerAccountPort;
    private final LedgerPostingPort ledgerPostingPort;

    public LedgerTradeSettlementService(LedgerAccountPort ledgerAccountPort, LedgerPostingPort ledgerPostingPort) {
        this.ledgerAccountPort = ledgerAccountPort;
        this.ledgerPostingPort = ledgerPostingPort;
    }

    @Override
    @Transactional
    public LedgerTradeSettlementResult settle(LedgerTradeSettlementCommand command) {
        BigDecimal notional = command.quantity().multiply(command.price()).stripTrailingZeros();
        List<PostingLineCommand> lines = pairedSettlementLines(command, notional);

        LedgerPostingResult result = ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.TRADE_SETTLEMENT,
            "trade-settlement:" + command.executionId(),
            command.idempotencyKey(),
            "settle trading execution",
            AuditMetadata.of(
                command.actorId(),
                "trading",
                command.buyerOrderId() + ":" + command.sellerOrderId(),
                command.executionId(),
                "trade execution settlement"
            ),
            lines
        ));
        return new LedgerTradeSettlementResult(result.transactionId(), result.idempotencyKey(), result.idempotentReplay());
    }

    private List<PostingLineCommand> pairedSettlementLines(LedgerTradeSettlementCommand command, BigDecimal notional) {
        List<PostingLineCommand> lines = new ArrayList<>();

        BigDecimal buyerQuoteDebit = notional.add(command.buyerFeeAssetCode().equals(command.quoteAsset()) ? command.buyerFeeAmount() : BigDecimal.ZERO)
            .stripTrailingZeros();
        addPositive(lines, userLocked(command.buyerUserId().toString(), command.quoteAsset()).accountId(), PostingDirection.DEBIT, buyerQuoteDebit);

        BigDecimal sellerQuoteCredit = notional.subtract(command.sellerFeeAssetCode().equals(command.quoteAsset()) ? command.sellerFeeAmount() : BigDecimal.ZERO)
            .stripTrailingZeros();
        addPositive(lines, userAvailable(command.sellerUserId().toString(), command.quoteAsset()).accountId(), PostingDirection.CREDIT, sellerQuoteCredit);
        addFeeCredit(lines, command.quoteAsset(), command.buyerFeeAssetCode(), command.buyerFeeAmount());
        addFeeCredit(lines, command.quoteAsset(), command.sellerFeeAssetCode(), command.sellerFeeAmount());

        BigDecimal sellerBaseDebit = command.quantity().add(command.sellerFeeAssetCode().equals(command.baseAsset()) ? command.sellerFeeAmount() : BigDecimal.ZERO)
            .stripTrailingZeros();
        addPositive(lines, userLocked(command.sellerUserId().toString(), command.baseAsset()).accountId(), PostingDirection.DEBIT, sellerBaseDebit);

        BigDecimal buyerBaseCredit = command.quantity().subtract(command.buyerFeeAssetCode().equals(command.baseAsset()) ? command.buyerFeeAmount() : BigDecimal.ZERO)
            .stripTrailingZeros();
        addPositive(lines, userAvailable(command.buyerUserId().toString(), command.baseAsset()).accountId(), PostingDirection.CREDIT, buyerBaseCredit);
        addFeeCredit(lines, command.baseAsset(), command.buyerFeeAssetCode(), command.buyerFeeAmount());
        addFeeCredit(lines, command.baseAsset(), command.sellerFeeAssetCode(), command.sellerFeeAmount());

        return lines;
    }

    private void addFeeCredit(List<PostingLineCommand> lines, String assetCode, String feeAssetCode, BigDecimal feeAmount) {
        if (feeAmount.signum() > 0 && assetCode.equals(feeAssetCode)) {
            addPositive(lines, fee(assetCode).accountId(), PostingDirection.CREDIT, feeAmount);
        }
    }

    private void addPositive(List<PostingLineCommand> lines, java.util.UUID accountId, PostingDirection direction, BigDecimal amount) {
        if (amount.signum() > 0) {
            lines.add(new PostingLineCommand(accountId, direction, amount));
        }
    }

    private LedgerAccountView userAvailable(String userId, String assetCode) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            userId,
            assetCode,
            BalanceType.AVAILABLE
        ));
    }

    private LedgerAccountView userLocked(String userId, String assetCode) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            userId,
            assetCode,
            BalanceType.LOCKED
        ));
    }

    private LedgerAccountView fee(String assetCode) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.FEE,
            "trading:fee:" + assetCode,
            assetCode,
            BalanceType.FEE
        ));
    }
}
