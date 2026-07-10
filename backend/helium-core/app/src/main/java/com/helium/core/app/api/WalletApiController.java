package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.wallet.application.DepositAddressQrCodeService;
import com.helium.core.wallet.application.RequestWithdrawalCommand;
import com.helium.core.wallet.application.WithdrawalAuthorizationService;
import com.helium.core.wallet.application.WithdrawalAuthorizationView;
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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final WalletAddressProvisioningService addressProvisioningService;
    private final DepositAddressQrCodeService depositAddressQrCodeService;
    private final WithdrawalAuthorizationService withdrawalAuthorizationService;

    public WalletApiController(
        TrustedActorProvider trustedActorProvider,
        ApiReadService readService,
        WithdrawalRequestPort withdrawalRequestPort,
        WalletAddressProvisioningService addressProvisioningService,
        DepositAddressQrCodeService depositAddressQrCodeService,
        WithdrawalAuthorizationService withdrawalAuthorizationService
    ) {
        this.trustedActorProvider = trustedActorProvider;
        this.readService = readService;
        this.withdrawalRequestPort = withdrawalRequestPort;
        this.addressProvisioningService = addressProvisioningService;
        this.depositAddressQrCodeService = depositAddressQrCodeService;
        this.withdrawalAuthorizationService = withdrawalAuthorizationService;
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

    @PostMapping("/withdrawals/{withdrawalId}/authorization/email")
    public WithdrawalAuthorizationView confirmWithdrawalEmail(
        @PathVariable UUID withdrawalId,
        @Valid @RequestBody WithdrawalEmailConfirmation request
    ) {
        return withdrawalAuthorizationService.confirmEmail(withdrawalId, request.token());
    }

    @PostMapping("/withdrawals/{withdrawalId}/authorization/email/resend")
    public WithdrawalAuthorizationView resendWithdrawalEmail(@PathVariable UUID withdrawalId) {
        return withdrawalAuthorizationService.resend(withdrawalId);
    }

    @PostMapping("/withdrawals/{withdrawalId}/authorization/mfa")
    public WithdrawalAuthorizationView confirmWithdrawalMfa(
        @PathVariable UUID withdrawalId,
        @Valid @RequestBody WithdrawalMfaConfirmation request,
        HttpServletRequest httpRequest
    ) {
        return withdrawalAuthorizationService.confirmMfa(withdrawalId, request.totpCode(), ApiSecurity.context(httpRequest));
    }

    @GetMapping("/addresses")
    public List<DepositAddressResponse> addresses() {
        return readService.addresses(requireUserId()).stream().map(this::toAddressResponse).toList();
    }

    @PostMapping("/addresses")
    public DepositAddressResponse createAddress(@Valid @RequestBody DepositAddressRequest request) {
        return toAddressResponse(addressProvisioningService.getOrCreate(requireUserId(), request.asset(), request.network()));
    }

    private DepositAddressResponse toAddressResponse(ApiReadService.AddressDto address) {
        var qrCode = depositAddressQrCodeService.generate(address.asset(), address.network(), address.address());
        return new DepositAddressResponse(
            address.id(),
            address.asset(),
            address.network(),
            address.address(),
            address.memo(),
            address.status(),
            address.createdAt(),
            qrCode.paymentUri(),
            qrCode.qrCodeDataUrl()
        );
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

    public record DepositAddressRequest(
        @NotBlank @Size(max = 32) String asset,
        @NotBlank @Size(max = 40) String network
    ) {}

    public record WithdrawalEmailConfirmation(@NotBlank @Size(max = 512) String token) {}

    public record WithdrawalMfaConfirmation(@NotBlank @Size(min = 6, max = 6) String totpCode) {}

    public record DepositAddressResponse(
        UUID id,
        String asset,
        String network,
        String address,
        String memo,
        String status,
        java.time.Instant createdAt,
        String paymentUri,
        String qrCodeDataUrl
    ) {}
}
