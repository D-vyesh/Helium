package com.helium.core.app.api;

import com.helium.core.authuser.application.SessionPort;
import com.helium.core.authuser.application.SessionView;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionPort sessionPort;

    public SessionAuthenticationFilter(SessionPort sessionPort) {
        this.sessionPort = sessionPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            ApiSecurity.bearerOrCookie(request)
                .flatMap(sessionPort::validate)
                .ifPresent(this::authenticate);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(SessionView session) {
        var authorities = session.roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            session.userId().toString(),
            "session",
            authorities
        ));
    }
}
