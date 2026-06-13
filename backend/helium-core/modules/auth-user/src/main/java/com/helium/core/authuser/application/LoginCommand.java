package com.helium.core.authuser.application;

public record LoginCommand(String email, String password, SecurityContextData securityContext) {
}
