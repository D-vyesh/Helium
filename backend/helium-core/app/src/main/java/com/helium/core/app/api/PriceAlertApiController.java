package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/price-alerts")
@Tag(name = "Price Alerts")
public class PriceAlertApiController {
    private final TrustedActorProvider trustedActorProvider;
    private final PriceAlertService priceAlertService;

    public PriceAlertApiController(TrustedActorProvider trustedActorProvider, PriceAlertService priceAlertService) {
        this.trustedActorProvider = trustedActorProvider;
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    public List<PriceAlertService.PriceAlertView> alerts() {
        return priceAlertService.list(requireUserId());
    }

    @PostMapping
    public PriceAlertService.PriceAlertView create(@Valid @RequestBody PriceAlertRequest request) {
        return priceAlertService.create(requireUserId(), new PriceAlertService.PriceAlertRequest(
            request.marketSymbol(),
            request.conditionType(),
            request.threshold(),
            request.repeating(),
            request.enabled(),
            request.deliveryInApp(),
            request.deliveryEmail(),
            request.deliveryPush(),
            request.expiresAt()
        ));
    }

    @PostMapping("/{id}/enable")
    public PriceAlertService.PriceAlertView enable(@PathVariable UUID id) {
        return priceAlertService.setEnabled(requireUserId(), id, true);
    }

    @PostMapping("/{id}/disable")
    public PriceAlertService.PriceAlertView disable(@PathVariable UUID id) {
        return priceAlertService.setEnabled(requireUserId(), id, false);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        priceAlertService.delete(requireUserId(), id);
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    public record PriceAlertRequest(
        @NotBlank String marketSymbol,
        @NotBlank String conditionType,
        @DecimalMin(value = "0.000000000000000001") BigDecimal threshold,
        boolean repeating,
        boolean enabled,
        boolean deliveryInApp,
        boolean deliveryEmail,
        boolean deliveryPush,
        Instant expiresAt
    ) {}
}
