package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.LedgerAccount;
import com.helium.core.ledger.domain.LedgerValidationException;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import com.helium.core.ledger.infrastructure.LedgerAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerAccountService implements LedgerAccountPort {
    private final LedgerAccountRepository ledgerAccountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;

    public LedgerAccountService(
        LedgerAccountRepository ledgerAccountRepository,
        BalanceSnapshotRepository balanceSnapshotRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
    }

    @Override
    @Transactional
    public LedgerAccountView openAccount(CreateLedgerAccountCommand command) {
        ledgerAccountRepository.insertIfAbsent(
            UUID.randomUUID(),
            command.ownerType().name(),
            command.ownerId(),
            command.assetCode(),
            command.balanceType().name(),
            command.negativeBalanceAllowed(),
            Instant.now()
        );

        LedgerAccount account = ledgerAccountRepository
            .findByOwnerTypeAndOwnerIdAndAssetCodeAndBalanceType(
                command.ownerType(),
                command.ownerId(),
                command.assetCode(),
                command.balanceType()
            )
            .orElseThrow(() -> new IllegalStateException("ledger account insert did not create or return an account"));

        if (account.negativeBalanceAllowed() != command.negativeBalanceAllowed()) {
            throw new LedgerValidationException("existing ledger account negative balance policy does not match requested policy");
        }

        balanceSnapshotRepository.insertZeroIfAbsent(
            UUID.randomUUID(),
            account.id(),
            account.assetCode(),
            Instant.now()
        );

        return new LedgerAccountView(account.id(), account.ownerType(), account.ownerId(), account.assetCode(), account.balanceType());
    }
}
