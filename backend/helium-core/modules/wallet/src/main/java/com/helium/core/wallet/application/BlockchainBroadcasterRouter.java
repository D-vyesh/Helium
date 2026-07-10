package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.WalletValidationException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BlockchainBroadcasterRouter {
    private final Map<String, BlockchainBroadcastService> broadcasters;

    public BlockchainBroadcasterRouter(List<BlockchainBroadcastService> broadcasters) {
        this.broadcasters = broadcasters.stream()
            .collect(Collectors.toUnmodifiableMap(
                broadcaster -> Asset.normalizeCode(broadcaster.assetCode()),
                Function.identity()
            ));
    }

    public BlockchainBroadcastService requiredBroadcaster(String assetCode) {
        BlockchainBroadcastService broadcaster = broadcasters.get(Asset.normalizeCode(assetCode));
        if (broadcaster == null) {
            throw new WalletValidationException("no blockchain broadcaster is configured for " + assetCode);
        }
        return broadcaster;
    }
}
