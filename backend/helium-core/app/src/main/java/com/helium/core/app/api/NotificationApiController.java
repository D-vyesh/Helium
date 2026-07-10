package com.helium.core.app.api;

import com.helium.core.authuser.application.TrustedActorProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationApiController {
    private final TrustedActorProvider trustedActorProvider;
    private final NotificationService notificationService;

    public NotificationApiController(TrustedActorProvider trustedActorProvider, NotificationService notificationService) {
        this.trustedActorProvider = trustedActorProvider;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationService.NotificationView> notifications(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return notificationService.list(requireUserId(), before, limit);
    }

    @GetMapping("/unread-count")
    public NotificationService.UnreadCount unreadCount() {
        return new NotificationService.UnreadCount(notificationService.unreadCount(requireUserId()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        notificationService.markRead(requireUserId(), id);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        notificationService.markAllRead(requireUserId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        notificationService.delete(requireUserId(), id);
    }

    private UUID requireUserId() {
        return trustedActorProvider.currentUserId().orElseThrow(() -> new ApiUnauthorizedException("authenticated session is required"));
    }
}
