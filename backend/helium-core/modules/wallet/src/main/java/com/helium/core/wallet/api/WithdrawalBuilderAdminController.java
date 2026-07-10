package com.helium.core.wallet.api;

import com.helium.core.wallet.application.UnsignedTransactionAdminService;
import com.helium.core.wallet.application.UnsignedTransactionAdminView;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only operational inspection endpoints; API security restricts this route to administrators. */
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
public class WithdrawalBuilderAdminController {
    private final UnsignedTransactionAdminService adminService;

    public WithdrawalBuilderAdminController(UnsignedTransactionAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/{withdrawalId}/builder")
    public ResponseEntity<UnsignedTransactionAdminView> builder(@PathVariable UUID withdrawalId) {
        return adminService.find(withdrawalId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{withdrawalId}/fee")
    public ResponseEntity<UnsignedTransactionAdminView> fee(@PathVariable UUID withdrawalId) {
        return adminService.find(withdrawalId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{withdrawalId}/unsigned")
    public ResponseEntity<UnsignedTransactionAdminView> unsigned(@PathVariable UUID withdrawalId) {
        return adminService.find(withdrawalId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
