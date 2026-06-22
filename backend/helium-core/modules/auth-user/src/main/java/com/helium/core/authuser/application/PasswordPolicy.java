package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;

final class PasswordPolicy {
    private PasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new AuthValidationException("password must contain between 12 and 128 characters");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            throw new AuthValidationException("password must contain at least one uppercase character");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            throw new AuthValidationException("password must contain at least one lowercase character");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            throw new AuthValidationException("password must contain at least one number");
        }
        if (password.chars().noneMatch(character -> !Character.isLetterOrDigit(character))) {
            throw new AuthValidationException("password must contain at least one special character");
        }
    }
}
