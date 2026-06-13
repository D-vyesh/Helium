package com.helium.core.authuser.application;

public interface AuthenticationPort {
    LoginResult login(LoginCommand command);
}
