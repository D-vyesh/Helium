package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys")
public class ApiKeyController {
    private final ApiKeyService apiKeyService;
    private final TrustedActorProvider trustedActorProvider;

    public ApiKeyController(ApiKeyService apiKeyService, TrustedActorProvider trustedActorProvider) {
        this.apiKeyService = apiKeyService;
        this.trustedActorProvider = trustedActorProvider;
    }

    @PostMapping
    public CreateApiKeyResponse create(@Valid @RequestBody CreateApiKeyRequest request) {
        UUID userId = requireUserId();
        ApiKeyService.CreatedApiKey created = apiKeyService.create(
            userId,
            request.label(),
            request.ipAllowlist() == null ? List.of() : request.ipAllowlist(),
            request.scopes() == null ? List.of("read") : request.scopes(),
            trustedActorProvider.currentActorId()
        );
        return new CreateApiKeyResponse(created.keyId(), created.secret(), created.scopes(), created.expiresAt());
    }

    @GetMapping
    public List<ApiKeyService.ApiKeyView> list() {
        UUID userId = requireUserId();
        return apiKeyService.listKeys(userId);
    }

    @PostMapping("/{keyId}/rotate")
    public CreateApiKeyResponse rotate(@PathVariable String keyId) {
        UUID userId = requireUserId();
        ApiKeyService.CreatedApiKey rotated = apiKeyService.rotate(userId, keyId, trustedActorProvider.currentActorId());
        return new CreateApiKeyResponse(rotated.keyId(), rotated.secret(), rotated.scopes(), rotated.expiresAt());
    }

    @DeleteMapping("/{keyId}")
    public RevokeApiKeyResponse revoke(@PathVariable String keyId) {
        UUID userId = requireUserId();
        apiKeyService.revoke(userId, keyId, trustedActorProvider.currentActorId());
        return new RevokeApiKeyResponse(true);
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    public record CreateApiKeyRequest(
        @NotBlank @Size(max = 120) String label,
        List<@Size(max = 64) String> ipAllowlist,
        List<@Size(max = 16) String> scopes
    ) {}

    public record CreateApiKeyResponse(String keyId, String secret, java.util.Set<String> scopes, java.time.Instant expiresAt) {}
    public record RevokeApiKeyResponse(boolean revoked) {}
}
