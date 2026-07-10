package com.helium.core.authuser.application;

public record TotpSetupResult(
    String secret,
    String otpAuthUrl,
    String qrCodeDataUrl
) {}
