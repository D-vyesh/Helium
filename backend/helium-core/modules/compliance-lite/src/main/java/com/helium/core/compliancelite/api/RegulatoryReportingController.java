package com.helium.core.compliancelite.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/regulatory/reports")
public class RegulatoryReportingController {

    @GetMapping("/sar")
    public ResponseEntity<Map<String, Object>> generateSuspiciousActivityReport() {
        return ResponseEntity.ok(Map.of(
            "reportId", "SAR-2026-0001",
            "jurisdiction", "US",
            "regulator", "FinCEN",
            "generatedAt", Instant.now().toString(),
            "status", "READY_FOR_FILING",
            "flaggedEntities", List.of(
                Map.of(
                    "userId", "mock-user-1",
                    "reason", "Abnormal withdrawal velocity",
                    "totalAmountUSD", 500000
                )
            )
        ));
    }

    @GetMapping("/fatf/travel-rule")
    public ResponseEntity<Map<String, Object>> getTravelRuleData() {
        return ResponseEntity.ok(Map.of(
            "txId", "0xtxhash",
            "originator", Map.of(
                "name", "John Doe",
                "physicalAddress", "123 Crypto Lane",
                "walletAddress", "0xOriginator"
            ),
            "beneficiary", Map.of(
                "name", "Jane Smith",
                "walletAddress", "0xBeneficiary"
            )
        ));
    }

    @GetMapping("/mica")
    public ResponseEntity<Map<String, Object>> generateMicaReport() {
        return ResponseEntity.ok(Map.of(
            "reportId", "MICA-2026-001",
            "jurisdiction", "EU",
            "regulator", "ESMA",
            "timestamp", Instant.now().toString(),
            "whitepaperComplianceStatus", "COMPLIANT",
            "environmentalImpact", "LOW"
        ));
    }

    @GetMapping("/ofac")
    public ResponseEntity<Map<String, Object>> generateOfacScreeningReport() {
        return ResponseEntity.ok(Map.of(
            "reportId", "OFAC-2026-001",
            "jurisdiction", "US",
            "regulator", "OFAC",
            "timestamp", Instant.now().toString(),
            "screenedUsers", 150000,
            "sanctionedHits", 0
        ));
    }
}
