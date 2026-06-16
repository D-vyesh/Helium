package com.helium.core.app.api;

import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.authuser.domain.Role;
import com.helium.core.outbox.application.OutboxReplayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for outbox monitoring and dead-letter replay operations.
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@Tag(name = "Admin - Outbox")
public class OutboxAdminController {
    private final OutboxReplayService replayService;
    private final TrustedActorProvider trustedActorProvider;
    private final AuthorizationPort authorizationPort;

    public OutboxAdminController(
        OutboxReplayService replayService,
        TrustedActorProvider trustedActorProvider,
        AuthorizationPort authorizationPort
    ) {
        this.replayService = replayService;
        this.trustedActorProvider = trustedActorProvider;
        this.authorizationPort = authorizationPort;
    }

    @GetMapping("/stats")
    public OutboxReplayService.OutboxStats stats() {
        requireAdmin();
        return replayService.stats();
    }

    @GetMapping("/dead-letters")
    public List<OutboxReplayService.DeadLetterView> deadLetters(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        requireAdmin();
        return replayService.deadLetters(Math.min(limit, 200), Math.max(offset, 0));
    }

    @PostMapping("/replay/{id}")
    public ReplayResponse replay(@PathVariable UUID id) {
        requireAdmin();
        boolean replayed = replayService.replayDeadLetter(id);
        return new ReplayResponse(replayed, replayed ? "Event replayed" : "Event not found or not dead-lettered");
    }

    @PostMapping("/replay-all")
    public ReplayAllResponse replayAll() {
        requireAdmin();
        int count = replayService.replayAllDeadLetters();
        return new ReplayAllResponse(count);
    }

    private void requireAdmin() {
        UUID userId = trustedActorProvider.currentUserId()
            .orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
        authorizationPort.requireRole(userId, Role.ADMIN);
    }

    public record ReplayResponse(boolean replayed, String message) {}
    public record ReplayAllResponse(int replayedCount) {}
}
