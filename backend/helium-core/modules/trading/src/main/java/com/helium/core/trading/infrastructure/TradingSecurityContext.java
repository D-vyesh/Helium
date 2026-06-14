package com.helium.core.trading.infrastructure;

import com.helium.core.authuser.application.TrustedActorProvider;
import com.helium.core.trading.domain.TradingValidationException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TradingSecurityContext {
    private final TrustedActorProvider trustedActorProvider;

    public TradingSecurityContext(TrustedActorProvider trustedActorProvider) {
        this.trustedActorProvider = trustedActorProvider;
    }

    public UUID requireUserId() {
        return trustedActorProvider.currentUserId()
            .orElseThrow(() -> new TradingValidationException("authenticated actor is required"));
    }

    public String actorId() {
        return trustedActorProvider.currentActorId();
    }
}
