package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.wallet.application.RequestWithdrawalCommand;
import com.helium.core.wallet.application.WithdrawalRequestPort;
import com.helium.core.wallet.application.WithdrawalView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet")
public class WalletApiController {
    private final TrustedActorProvider trustedActorProvider;
    private final ApiReadService readService;
    private final WithdrawalRequestPort withdrawalRequestPort;

    public WalletApiController(TrustedActorProvider trustedActorProvider, ApiReadService readService, WithdrawalRequestPort withdrawalRequestPort) {
        this.trustedActorProvider = trustedActorProvider;
        this.readService = readService;
        this.withdrawalRequestPort = withdrawalRequestPort;
    }

    @GetMapping("/balances")
    public List<ApiReadService.BalanceDto> balances() {
        return readService.balances(requireUserId());
    }

    @GetMapping("/deposits")
    public List<ApiReadService.DepositDto> deposits() {
        return readService.deposits(requireUserId());
    }

    @GetMapping("/withdrawals")
    public List<ApiReadService.WithdrawalDto> withdrawals() {
        return readService.withdrawals(requireUserId());
    }

    @PostMapping("/withdrawals")
    public WithdrawalView requestWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        return withdrawalRequestPort.requestWithdrawal(new RequestWithdrawalCommand(
            request.clientRequestId(),
            request.asset(),
            request.network(),
            request.destination(),
            request.memo(),
            request.amount()
        ));
    }

    @GetMapping("/addresses")
    public List<ApiReadService.AddressDto> addresses() {
        return readService.addresses(requireUserId());
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    public record WithdrawalRequest(
        @NotBlank @Size(max = 120) String clientRequestId,
        @NotBlank @Size(max = 32) String asset,
        @NotBlank @Size(max = 40) String network,
        @NotBlank @Size(max = 160) String destination,
        @Size(max = 120) String memo,
        @DecimalMin(value = "0.000000000000000001") BigDecimal amount
    ) {}
}
