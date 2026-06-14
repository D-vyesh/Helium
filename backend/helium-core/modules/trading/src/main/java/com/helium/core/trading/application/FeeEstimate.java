package com.helium.core.trading.application;

import com.helium.core.trading.domain.FeeAssetType;
import java.math.BigDecimal;

record FeeEstimate(BigDecimal amount, FeeAssetType assetType, String assetCode, BigDecimal rate, String policyVersion) {
}
