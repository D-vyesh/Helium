package com.helium.core.ledger.domain;

public enum LedgerAccountOwnerType {
    USER(PostingDirection.CREDIT),
    EXCHANGE(PostingDirection.DEBIT),
    FEE(PostingDirection.CREDIT),
    CLEARING(PostingDirection.DEBIT),
    EXTERNAL(PostingDirection.DEBIT),
    SUSPENSE(PostingDirection.DEBIT);

    private final PostingDirection normalBalanceDirection;

    LedgerAccountOwnerType(PostingDirection normalBalanceDirection) {
        this.normalBalanceDirection = normalBalanceDirection;
    }

    public PostingDirection normalBalanceDirection() {
        return normalBalanceDirection;
    }
}
