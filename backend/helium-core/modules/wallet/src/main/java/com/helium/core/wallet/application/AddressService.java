package com.helium.core.wallet.application;

import com.helium.core.authuser.application.AccountStatusPort;
import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.DepositAddress;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.domain.WalletValidationException;
import com.helium.core.wallet.infrastructure.AssetRepository;
import com.helium.core.wallet.infrastructure.BlockchainNetworkRepository;
import com.helium.core.wallet.infrastructure.DepositAddressRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService implements AddressPort {
    private final DepositAddressRepository addressRepository;
    private final AssetRepository assetRepository;
    private final BlockchainNetworkRepository networkRepository;
    private final AccountStatusPort accountStatusPort;
    private final WalletActorService actorService;
    private final WalletAuditService auditService;
    private final Clock clock;

    public AddressService(
        DepositAddressRepository addressRepository,
        AssetRepository assetRepository,
        BlockchainNetworkRepository networkRepository,
        AccountStatusPort accountStatusPort,
        WalletActorService actorService,
        WalletAuditService auditService,
        Clock clock
    ) {
        this.addressRepository = addressRepository;
        this.assetRepository = assetRepository;
        this.networkRepository = networkRepository;
        this.accountStatusPort = accountStatusPort;
        this.actorService = actorService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DepositAddressView assignDepositAddress(AssignDepositAddressCommand command) {
        UUID userId = actorService.requireCurrentUserId();
        String actorId = userId.toString();
        if (!accountStatusPort.isActive(userId)) {
            throw new WalletValidationException("user account is not active");
        }
        Asset asset = assetRepository.findById(Asset.normalizeCode(command.assetCode()))
            .orElseThrow(() -> new WalletValidationException("asset is not registered"));
        BlockchainNetwork network = networkRepository.findById(BlockchainNetwork.normalizeNetworkCode(command.networkCode()))
            .orElseThrow(() -> new WalletValidationException("network is not registered"));
        if (!asset.depositEnabled() || !network.depositEnabled()) {
            throw new WalletValidationException("deposits are disabled");
        }
        if (!network.assetCode().equals(asset.code())) {
            throw new WalletValidationException("network does not support asset");
        }

        DepositAddress address = addressRepository
            .findByUserIdAndAssetCodeAndNetworkCode(userId, asset.code(), network.networkCode())
            .orElseGet(() -> addressRepository.save(DepositAddress.assign(
                userId,
                asset.code(),
                network.networkCode(),
                command.address(),
                command.memo(),
                clock.instant()
            )));
        auditService.record(WalletAuditEventType.DEPOSIT_ADDRESS_ASSIGNED, address.id(), actorId, address.address());
        return toView(address);
    }

    static DepositAddressView toView(DepositAddress address) {
        return new DepositAddressView(
            address.id(),
            address.userId(),
            address.assetCode(),
            address.networkCode(),
            address.address(),
            address.memo()
        );
    }
}
