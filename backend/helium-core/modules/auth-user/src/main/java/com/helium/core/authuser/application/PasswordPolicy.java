package com.helium.core.authuser.application;

import com.helium.core.authuser.domain.AuthValidationException;

final class PasswordPolicy {
    private PasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new AuthValidationException("password must contain between 12 and 128 characters");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw new AuthValidationException("password must contain at least one letter and one digit");
        }
    }
}
