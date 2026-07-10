package com.helium.core.wallet.api;

import com.helium.core.wallet.application.BroadcastAdminService;
import com.helium.core.wallet.application.BroadcastAdminView;
import com.helium.core.wallet.application.ReorgAdminView;
import com.helium.core.wallet.application.TransactionAdminView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class BroadcastAdminController {
    private final BroadcastAdminService adminService;

    public BroadcastAdminController(BroadcastAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/broadcasts")
    public List<BroadcastAdminView> broadcasts() {
        return adminService.broadcasts();
    }

    @GetMapping("/transactions")
    public List<TransactionAdminView> transactions() {
        return adminService.transactions();
    }

    @GetMapping("/transactions/{withdrawalId}")
    public ResponseEntity<TransactionAdminView> transaction(@PathVariable UUID withdrawalId) {
        return adminService.transaction(withdrawalId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/reorgs")
    public List<ReorgAdminView> reorgs() {
        return adminService.reorgs();
    }

    @GetMapping("/stuck-transactions")
    public List<BroadcastAdminView> stuckTransactions() {
        return adminService.stuckTransactions();
    }
}
