package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.PostingDirection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundsReservationService implements FundsReservationPort {
    private final LedgerAccountPort ledgerAccountPort;
    private final LedgerPostingPort ledgerPostingPort;

    public FundsReservationService(LedgerAccountPort ledgerAccountPort, LedgerPostingPort ledgerPostingPort) {
        this.ledgerAccountPort = ledgerAccountPort;
        this.ledgerPostingPort = ledgerPostingPort;
    }

    @Override
    @Transactional
    public FundsReservationResult reserve(ReserveFundsCommand command) {
        LedgerAccountView available = userAccount(command, BalanceType.AVAILABLE);
        LedgerAccountView locked = userAccount(command, BalanceType.LOCKED);
        LedgerPostingResult result = ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.TRADE_SETTLEMENT,
            command.businessReference(),
            command.idempotencyKey(),
            "reserve trading funds",
            audit(command),
            List.of(
                new PostingLineCommand(available.accountId(), PostingDirection.DEBIT, command.amount()),
                new PostingLineCommand(locked.accountId(), PostingDirection.CREDIT, command.amount())
            )
        ));
        return new FundsReservationResult(result.transactionId(), result.idempotencyKey(), result.idempotentReplay());
    }

    @Override
    @Transactional
    public FundsReservationResult release(ReleaseFundsCommand command) {
        LedgerAccountView available = userAccount(command, BalanceType.AVAILABLE);
        LedgerAccountView locked = userAccount(command, BalanceType.LOCKED);
        LedgerPostingResult result = ledgerPostingPort.post(new LedgerPostingCommand(
            LedgerTransactionType.TRADE_SETTLEMENT,
            command.businessReference(),
            command.idempotencyKey(),
            "release trading reservation",
            AuditMetadata.of(
                command.actorId(),
                "trading",
                command.businessReference(),
                command.idempotencyKey(),
                command.reason()
            ),
            List.of(
                new PostingLineCommand(locked.accountId(), PostingDirection.DEBIT, command.amount()),
                new PostingLineCommand(available.accountId(), PostingDirection.CREDIT, command.amount())
            )
        ));
        return new FundsReservationResult(result.transactionId(), result.idempotencyKey(), result.idempotentReplay());
    }

    private LedgerAccountView userAccount(ReserveFundsCommand command, BalanceType balanceType) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            command.userId().toString(),
            command.assetCode(),
            balanceType
        ));
    }

    private LedgerAccountView userAccount(ReleaseFundsCommand command, BalanceType balanceType) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            command.userId().toString(),
            command.assetCode(),
            balanceType
        ));
    }

    private static AuditMetadata audit(ReserveFundsCommand command) {
        return AuditMetadata.of(
            command.actorId(),
            "trading",
            command.businessReference(),
            command.idempotencyKey(),
            command.reason()
        );
    }
}
