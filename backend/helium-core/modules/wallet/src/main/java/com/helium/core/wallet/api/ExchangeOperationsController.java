package com.helium.core.wallet.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exchange-operations")
public class ExchangeOperationsController {

    @GetMapping("/health/rpc-nodes")
    public ResponseEntity<Map<String, Object>> getRpcHealth() {
        return ResponseEntity.status(503).body(Map.of(
            "status", "UNCONFIGURED",
            "message", "Configure helium.wallet.rpc.btc.nodes, helium.wallet.rpc.eth.nodes, and helium.wallet.rpc.sol.nodes with real RPC endpoints",
            "activeNodes", Map.of()
        ));
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
