package com.helium.core.authuser.application;

public interface RegistrationPort {
    RegistrationResult register(RegistrationCommand command);
}
