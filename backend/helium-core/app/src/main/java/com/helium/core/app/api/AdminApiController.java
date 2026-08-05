package com.helium.core.app.api;

import com.helium.core.admin.application.AdminSecurityService;
import com.helium.core.admin.application.ReconciliationPort;
import com.helium.core.wallet.application.ApproveWithdrawalCommand;
import com.helium.core.wallet.application.RejectWithdrawalCommand;
import com.helium.core.wallet.application.WithdrawalApprovalPort;
import com.helium.core.wallet.application.WithdrawalView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminApiController {
    private final AdminSecurityService securityService;
    private final ApiReadService readService;
    private final ReconciliationPort reconciliationPort;
    private final WithdrawalApprovalPort withdrawalApprovalPort;

    public AdminApiController(
        AdminSecurityService securityService,
        ApiReadService readService,
        ReconciliationPort reconciliationPort,
        WithdrawalApprovalPort withdrawalApprovalPort
    ) {
        this.securityService = securityService;
        this.readService = readService;
        this.reconciliationPort = reconciliationPort;
        this.withdrawalApprovalPort = withdrawalApprovalPort;
    }

    @GetMapping("/users")
    public List<ApiReadService.UserDto> users() {
        securityService.requireAdminActor();
        return readService.users();
    }

    @GetMapping("/audit")
    public List<ApiReadService.AuditDto> audit() {
        securityService.requireAdminActor();
        return readService.adminAudit();
    }

    @GetMapping("/reconciliation")
    public List<ApiReadService.ReconciliationDto> reconciliation(@RequestParam(defaultValue = "false") boolean run) {
        securityService.requireAdminActor();
        if (run) {
            reconciliationPort.runDailyReconciliation(LocalDate.now());
        }
        return readService.reconciliations();
    }

    @GetMapping("/reconciliation/discrepancies")
    public List<ApiReadService.ReconciliationDiscrepancyDto> reconciliationDiscrepancies() {
        securityService.requireAdminActor();
        return readService.reconciliationDiscrepancies();
    }

    @GetMapping(value = "/reconciliation.csv", produces = "text/csv")
    public String reconciliationCsv() {
        securityService.requireAdminActor();
        StringBuilder csv = new StringBuilder("id,type,status,scope,difference,createdAt\n");
        for (ApiReadService.ReconciliationDto report : readService.reconciliations()) {
            csv.append(report.id()).append(',')
                .append(report.type()).append(',')
                .append(report.status()).append(',')
                .append(report.scope()).append(',')
                .append(report.difference()).append(',')
                .append(report.createdAt()).append('\n');
        }
        return csv.toString();
    }

    @GetMapping("/markets")
    public List<ApiReadService.AdminMarketDto> adminMarkets() {
        securityService.requireAdminActor();
        return readService.adminMarkets();
    }

    @GetMapping("/withdrawals/pending")
    public List<ApiReadService.WithdrawalDto> pendingWithdrawals() {
        securityService.requireAdminActor();
        return readService.pendingWithdrawals();
    }

    @PostMapping("/withdrawals/{withdrawalId}/approve")
    public WithdrawalView approveWithdrawal(@PathVariable UUID withdrawalId) {
        securityService.requireAdminActor();
        return withdrawalApprovalPort.approve(new ApproveWithdrawalCommand(withdrawalId));
    }

    @PostMapping("/withdrawals/{withdrawalId}/reject")
    public WithdrawalView rejectWithdrawal(
        @PathVariable UUID withdrawalId,
        @Valid @RequestBody RejectWithdrawalRequest request
    ) {
        securityService.requireAdminActor();
        return withdrawalApprovalPort.reject(new RejectWithdrawalCommand(withdrawalId, request.reason()));
    }

    public record RejectWithdrawalRequest(@NotBlank @Size(max = 500) String reason) {}
}
