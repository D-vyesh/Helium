package com.helium.core.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transparency")
public class TransparencyPortalController {

    @GetMapping("/proof-of-reserves")
    public ResponseEntity<Map<String, Object>> getProofOfReserves() {
        return ResponseEntity.ok(Map.of(
            "timestamp", Instant.now().toString(),
            "status", "VERIFIED",
            "merkleRootHash", "0xabc123merklehash",
            "totalLiabilitiesUsd", "950000.00",
            "totalOnChainAssetsUsd", "1000000.00",
            "reserveRatio", "1.05",
            "auditorSignature", "mock_signature"
        ));
    }

    @GetMapping("/system-status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        return ResponseEntity.ok(Map.of(
            "overallStatus", "OPERATIONAL",
            "incidents", List.of(),
            "lastUpdated", Instant.now().toString()
        ));
    }
}
