package com.helium.core.authuser.application;

import java.util.List;

public record TotpConfirmResult(boolean enabled, List<String> backupCodes) {}
