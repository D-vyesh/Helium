package com.helium.core.authuser.application;

import java.util.UUID;

public record UserProfileView(UUID userId, String email, String displayName) {}
