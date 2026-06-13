package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.BalanceSnapshot;
import com.helium.core.ledger.domain.LedgerAccount;
import com.helium.core.ledger.domain.LedgerValidationException;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import com.helium.core.ledger.infrastructure.LedgerAccountRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BalanceQueryService implements BalanceQueryPort {
    private final LedgerAccountRepository ledgerAccountRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;

    public BalanceQueryService(
        LedgerAccountRepository ledgerAccountRepository,
        BalanceSnapshotRepository balanceSnapshotRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSnapshotView getBalance(UUID accountId) {
        LedgerAccount account = ledgerAccountRepository.findById(accountId)
            .orElseThrow(() -> new LedgerValidationException("ledger account was not found"));

        BigDecimal balance = balanceSnapshotRepository.findByAccount_Id(account.id())
            .map(BalanceSnapshot::currentBalance)
            .orElse(BigDecimal.ZERO);

        return new BalanceSnapshotView(account.id(), account.assetCode(), balance);
    }
}

