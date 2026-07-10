package com.helium.core.authuser.application;

import java.util.UUID;

/** Read-only user profile data needed by authenticated cross-module workflows. */
public interface UserProfilePort {
    UserProfileView requireProfile(UUID userId);
}
