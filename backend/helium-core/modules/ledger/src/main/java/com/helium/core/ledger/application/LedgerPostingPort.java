package com.helium.core.ledger.application;

public interface LedgerPostingPort {
    LedgerPostingResult post(LedgerPostingCommand command);
}

