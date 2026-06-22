package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.application.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class Argon2idPasswordHasher implements PasswordHasher {
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
