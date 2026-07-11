package com.helium.core.wallet.api;

import com.helium.core.wallet.application.BlockchainHealthAdminView;
import com.helium.core.wallet.application.BlockchainProviderAdminService;
import com.helium.core.wallet.infrastructure.rpc.RpcProviderHealthState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exchange-operations")
public class ExchangeOperationsController {
    private final BlockchainProviderAdminService providerAdminService;

    public ExchangeOperationsController(BlockchainProviderAdminService providerAdminService) {
        this.providerAdminService = providerAdminService;
    }

    @GetMapping("/health/rpc-nodes")
    public ResponseEntity<Map<String, Object>> getRpcHealth() {
        BlockchainHealthAdminView health = providerAdminService.health();
        Map<String, Object> body = Map.of(
            "status", health.overallState().name(),
            "healthyProviders", health.healthyProviders(),
            "degradedProviders", health.degradedProviders(),
            "unavailableProviders", health.unavailableProviders(),
            "providers", health.providers()
        );
        return health.overallState() == RpcProviderHealthState.UNAVAILABLE
            ? ResponseEntity.status(503).body(body)
            : ResponseEntity.ok(body);
    }

    @GetMapping("/custody/dashboard")
    public ResponseEntity<Map<String, Object>> getCustodyDashboard() {
        return ResponseEntity.ok(Map.of(
            "hotWalletHealth", "OK",
            "coldWalletQueueSize", 0,
            "totalPendingWithdrawals", 0
        ));
    }

    @GetMapping("/custody/risk")
    public ResponseEntity<Map<String, Object>> getRiskDashboard() {
        return ResponseEntity.ok(Map.of(
            "activeVelocityFlags", 0,
            "activeSanctionsFlags", 0,
            "frozenAccounts", 0
        ));
    }
}
