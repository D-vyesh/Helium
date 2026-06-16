package com.helium.core.wallet.api;

import com.helium.core.ledger.application.ProofOfReserveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/por")
public class ProofOfReserveController {
    private final ProofOfReserveService porService;

    public ProofOfReserveController(ProofOfReserveService porService) {
        this.porService = porService;
    }

    @GetMapping("/snapshot/{assetCode}")
    public ResponseEntity<ProofOfReserveService.PoRSnapshot> getSnapshot(@PathVariable String assetCode) {
        return ResponseEntity.ok(porService.generateSnapshot(assetCode));
    }
}
