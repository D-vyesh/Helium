package com.helium.core.ledger.api;

import com.helium.core.ledger.application.ProofOfReserveService;
import com.helium.core.ledger.domain.LedgerValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transparency")
public class TransparencyPortalController {
    private final ProofOfReserveService proofOfReserveService;

    public TransparencyPortalController(ProofOfReserveService proofOfReserveService) {
        this.proofOfReserveService = proofOfReserveService;
    }

    @GetMapping("/proof-of-reserves")
    public ResponseEntity<LiabilitiesSnapshotResponse> getProofOfReserves(@RequestParam String asset) {
        String assetCode = asset == null ? "" : asset.trim().toUpperCase();
        if (assetCode.isBlank()) {
            throw new LedgerValidationException("asset is required");
        }
        ProofOfReserveService.PoRSnapshot snapshot = proofOfReserveService.generateSnapshot(assetCode);
        return ResponseEntity.ok(new LiabilitiesSnapshotResponse(
            assetCode,
            snapshot.totalLiabilities(),
            snapshot.merkleRoot(),
            "LIABILITIES_SNAPSHOT",
            "External reserve attestation is required before any reserve ratio can be published."
        ));
    }

    public record LiabilitiesSnapshotResponse(
        String asset,
        java.math.BigDecimal totalLiabilities,
        String merkleRoot,
        String status,
        String reserveAttestation
    ) {}
}
