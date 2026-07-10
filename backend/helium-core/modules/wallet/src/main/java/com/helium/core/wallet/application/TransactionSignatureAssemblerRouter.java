package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.WalletValidationException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TransactionSignatureAssemblerRouter {
    private final Map<String, TransactionSignatureAssembler> assemblers;

    public TransactionSignatureAssemblerRouter(List<TransactionSignatureAssembler> assemblers) {
        this.assemblers = assemblers.stream().collect(Collectors.toUnmodifiableMap(
            assembler -> Asset.normalizeCode(assembler.assetCode()),
            Function.identity(),
            (left, right) -> { throw new IllegalStateException("duplicate transaction signature assembler"); }
        ));
    }

    public TransactionSignatureAssembler requiredAssembler(String assetCode) {
        String asset = Asset.normalizeCode(assetCode);
        TransactionSignatureAssembler assembler = assemblers.get(asset);
        if (assembler == null) {
            throw new WalletValidationException("no transaction signature assembler is configured for " + asset);
        }
        return assembler;
    }
}
