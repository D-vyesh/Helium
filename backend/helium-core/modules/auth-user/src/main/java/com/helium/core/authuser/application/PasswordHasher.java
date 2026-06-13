package com.helium.core.authuser.application;

public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
