package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
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
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        ApiKeyService.CreatedApiKey created = apiKeyService.create(
            userId,
            request.label(),
            request.ipAllowlist() == null ? List.of() : request.ipAllowlist(),
            trustedActorProvider.currentActorId()
        );
        return new CreateApiKeyResponse(created.keyId(), created.secret());
    }

    @DeleteMapping("/{keyId}")
    public RevokeApiKeyResponse revoke(@PathVariable String keyId) {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        apiKeyService.revoke(userId, keyId, trustedActorProvider.currentActorId());
        return new RevokeApiKeyResponse(true);
    }

    public record CreateApiKeyRequest(
        @NotBlank @Size(max = 120) String label,
        List<@Size(max = 64) String> ipAllowlist
    ) {}

    public record CreateApiKeyResponse(String keyId, String secret) {}

    public record RevokeApiKeyResponse(boolean revoked) {}
}
