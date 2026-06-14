package com.helium.core.ledger.application;

public interface TradeSettlementPort {
    LedgerTradeSettlementResult settle(LedgerTradeSettlementCommand command);
}
