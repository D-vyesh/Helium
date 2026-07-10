package com.helium.core.app.api;

import com.helium.core.wallet.application.AddressPort;
import com.helium.core.wallet.application.AssignDepositAddressCommand;
import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.infrastructure.blockchain.BlockchainProvider;
import com.helium.core.wallet.infrastructure.blockchain.BlockchainProviderRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletAddressProvisioningService {
    private final ApiReadService readService;
    private final AddressPort addressPort;
    private final BlockchainProviderRegistry providerRegistry;

    public WalletAddressProvisioningService(
        ApiReadService readService,
        AddressPort addressPort,
        BlockchainProviderRegistry providerRegistry
    ) {
        this.readService = readService;
        this.addressPort = addressPort;
        this.providerRegistry = providerRegistry;
    }

    @Transactional
    public ApiReadService.AddressDto getOrCreate(UUID userId, String assetCode, String networkCode) {
        String asset = Asset.normalizeCode(assetCode);
        String network = BlockchainNetwork.normalizeNetworkCode(networkCode);
        List<ApiReadService.AddressDto> existing = readService.addresses(userId);
        for (ApiReadService.AddressDto address : existing) {
            if (asset.equals(address.asset()) && network.equals(address.network())) {
                return address;
            }
        }
        BlockchainProvider provider = providerRegistry.getRequiredProvider(network);
        String address = provider.generateAddress(asset, userId);
        addressPort.assignDepositAddress(new AssignDepositAddressCommand(asset, network, address, null));
        return readService.addresses(userId).stream()
            .filter(item -> asset.equals(item.asset()) && network.equals(item.network()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("deposit address was not persisted"));
    }
}
