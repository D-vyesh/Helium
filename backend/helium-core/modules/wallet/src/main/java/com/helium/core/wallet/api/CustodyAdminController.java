package com.helium.core.wallet.api;

import com.helium.core.wallet.application.CustodyAdminService;
import com.helium.core.wallet.application.CustodyAuditAdminView;
import com.helium.core.wallet.application.CustodyKeyAdminView;
import com.helium.core.wallet.application.CustodySignatureAdminView;
import com.helium.core.wallet.application.SignatureStatusAdminView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class CustodyAdminController {
    private final CustodyAdminService adminService;

    public CustodyAdminController(CustodyAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/custody/keys")
    public List<CustodyKeyAdminView> keys() {
        return adminService.keys();
    }

    @GetMapping("/custody/signatures")
    public List<CustodySignatureAdminView> signatures() {
        return adminService.signatures();
    }

    @GetMapping("/withdrawals/{withdrawalId}/signature-status")
    public ResponseEntity<SignatureStatusAdminView> signatureStatus(@PathVariable UUID withdrawalId) {
        return adminService.signatureStatus(withdrawalId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/withdrawals/{withdrawalId}/audit")
    public List<CustodyAuditAdminView> audit(@PathVariable UUID withdrawalId) {
        return adminService.audit(withdrawalId);
    }
}
