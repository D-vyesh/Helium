package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.WalletValidationException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UnsignedTransactionBuilderRouter {
    private final Map<String, UnsignedTransactionBuilder> builders;

    public UnsignedTransactionBuilderRouter(List<UnsignedTransactionBuilder> builders) {
        this.builders = builders.stream().collect(Collectors.toUnmodifiableMap(
            builder -> Asset.normalizeCode(builder.assetCode()),
            Function.identity(),
            (left, right) -> { throw new IllegalStateException("duplicate unsigned transaction builder"); }
        ));
    }

    public UnsignedTransactionBuilder requiredBuilder(String assetCode) {
        String asset = Asset.normalizeCode(assetCode);
        UnsignedTransactionBuilder builder = builders.get(asset);
        if (builder == null) {
            throw new WalletValidationException("no unsigned transaction builder is configured for " + asset);
        }
        return builder;
    }
}
