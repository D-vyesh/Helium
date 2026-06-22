package com.helium.core.app.api;

import com.helium.core.authuser.application.SecurityContextData;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;

final class ApiSecurity {
    static final String SESSION_COOKIE = "HELIUM_SESSION";

    private ApiSecurity() {
    }

    static SecurityContextData context(HttpServletRequest request) {
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
            .map(value -> value.split(",")[0].trim())
            .filter(value -> !value.isBlank())
            .orElseGet(request::getRemoteAddr);
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
        String deviceInfo = Optional.ofNullable(request.getHeader("X-Device-Info")).orElse(userAgent);
        return new SecurityContextData(ip, userAgent, deviceInfo);
    }

    static Optional<String> bearerOrCookie(HttpServletRequest request) {
        Optional<String> bearer = bearerToken(request);
        if (bearer.isPresent()) {
            return bearer;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> !value.isBlank())
            .findFirst();
    }

    static Optional<String> bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return Optional.of(authorization.substring("Bearer ".length()).trim()).filter(value -> !value.isBlank());
        }
        return Optional.empty();
    }
}
