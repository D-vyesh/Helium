package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Withdrawal;

public interface UnsignedTransactionBuilder {
    String assetCode();

    UnsignedTransactionDraft build(Withdrawal withdrawal, FeeTier feeTier);
}
