package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.AssetRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService implements AssetPort {
    private final AssetRepository assetRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final WalletLedgerAccounts ledgerAccounts;
    private final WalletActorService actorService;
    private final WalletAuditService auditService;
    private final Clock clock;

    public AssetService(
        AssetRepository assetRepository,
        BlockchainNetworkRepository networkRepository,
        WalletLedgerAccounts ledgerAccounts,
        WalletActorService actorService,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.assetRepository = assetRepository;
        this.networkRepository = networkRepository;
        this.ledgerAccounts = ledgerAccounts;
        this.actorService = actorService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AssetView registerAsset(RegisterAssetCommand command) {
        String actorId = actorService.requireOperationsActor();
        String assetCode = Asset.normalizeCode(command.assetCode());
        Asset asset = assetRepository.findById(assetCode)
            .orElseGet(() -> assetRepository.save(Asset.register(
                assetCode,
                command.name(),
                command.scale(),
                command.depositEnabled(),
                command.withdrawalEnabled(),
                clock.instant()
            )));
        auditService.record(WalletAuditEventType.ASSET_REGISTERED, null, actorId, assetCode);
        return toView(asset);
    }

    @Override
    @Transactional
    public NetworkView registerNetwork(RegisterNetworkCommand command) {
        String actorId = actorService.requireOperationsActor();
        String assetCode = Asset.normalizeCode(command.assetCode());
        if (!assetRepository.existsById(assetCode)) {
            throw new WalletValidationException("asset is not registered");
        }
        String networkCode = BlockchainNetwork.normalizeNetworkCode(command.networkCode());
        BlockchainNetwork network = networkRepository.findById(networkCode)
            .orElseGet(() -> networkRepository.save(BlockchainNetwork.register(
                networkCode,
                assetCode,
                command.displayName(),
                command.requiredConfirmations(),
                command.depositEnabled(),
                command.withdrawalEnabled(),
                command.minimumWithdrawal(),
                command.withdrawalFee(),
                clock.instant()
            )));
        ledgerAccounts.external(network.networkCode(), network.assetCode());
        ledgerAccounts.fee(network.assetCode());
        auditService.record(WalletAuditEventType.NETWORK_REGISTERED, null, actorId, networkCode);
        return toView(network);
    }

    private static AssetView toView(Asset asset) {
        return new AssetView(asset.code(), asset.scale(), asset.depositEnabled(), asset.withdrawalEnabled());
    }

    private static NetworkView toView(BlockchainNetwork network) {
        return new NetworkView(
            network.networkCode(),
            network.assetCode(),
            network.requiredConfirmations(),
            network.depositEnabled(),
            network.withdrawalEnabled(),
            network.minimumWithdrawal(),
            network.withdrawalFee()
        );
    }
}
