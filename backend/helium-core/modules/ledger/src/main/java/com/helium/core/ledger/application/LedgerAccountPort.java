package com.helium.core.ledger.application;

public interface LedgerAccountPort {
    LedgerAccountView openAccount(CreateLedgerAccountCommand command);
}

