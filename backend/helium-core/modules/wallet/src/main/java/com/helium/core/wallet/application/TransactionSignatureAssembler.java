package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.UnsignedTransaction;

public interface TransactionSignatureAssembler {
    String assetCode();

    SigningRequest prepare(UnsignedTransaction unsignedTransaction, CustodyKey custodyKey);

    SignedTransactionDraft assemble(UnsignedTransaction unsignedTransaction, SigningResult signingResult, CustodyKey custodyKey);
}
