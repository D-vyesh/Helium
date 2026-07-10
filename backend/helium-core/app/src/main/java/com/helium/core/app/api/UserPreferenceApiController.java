package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
@Tag(name = "User Preferences")
public class UserPreferenceApiController {
    private final TrustedActorProvider trustedActorProvider;
    private final UserPreferenceService preferenceService;

    public UserPreferenceApiController(TrustedActorProvider trustedActorProvider, UserPreferenceService preferenceService) {
        this.trustedActorProvider = trustedActorProvider;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public UserPreferenceService.UserPreferenceView preferences() {
        return preferenceService.get(requireUserId());
    }

    @PutMapping
    public UserPreferenceService.UserPreferenceView update(
        @RequestBody PreferenceRequest request,
        HttpServletRequest httpRequest
    ) {
        return preferenceService.update(
            requireUserId(),
            new UserPreferenceService.PreferenceUpdate(
                request.theme(),
                request.timezone(),
                request.language(),
                request.preferredFiat(),
                request.chartInterval(),
                request.chartStyle(),
                request.defaultMarket(),
                request.sidebarLayout(),
                request.workspaceLayout(),
                request.orderDefaults(),
                request.notificationPreferences()
            ),
            ApiSecurity.context(httpRequest)
        );
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }

    public record PreferenceRequest(
        String theme,
        String timezone,
        String language,
        String preferredFiat,
        String chartInterval,
        String chartStyle,
        String defaultMarket,
        String sidebarLayout,
        Map<String, Object> workspaceLayout,
        Map<String, Object> orderDefaults,
        Map<String, Object> notificationPreferences
    ) {}
}
