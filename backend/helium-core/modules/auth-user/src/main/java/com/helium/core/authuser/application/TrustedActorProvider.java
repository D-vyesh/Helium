package com.helium.core.authuser.application;

import java.util.Optional;
import java.util.UUID;

public interface TrustedActorProvider {
    String currentActorId();

    Optional<UUID> currentUserId();
}
