package com.helium.core.wallet.application;

import java.math.BigDecimal;

/** Chain-specific unsigned payload prepared for persistence and custody signing. */
public record UnsignedTransactionDraft(
    String format,
    String builderVersion,
    String serializedPayload,
    String psbt,
    Long nonce,
    String recentBlockhash,
    BigDecimal fee,
    String metadata
) {}
