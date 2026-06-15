package com.helium.core.app.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String SIGNATURE_HEADER = "X-API-Signature";
    private static final String TIMESTAMP_HEADER = "X-API-Timestamp";
    private static final String BODY_HASH_HEADER = "X-API-Body-SHA256";
    private final ApiKeyService apiKeyService;
    private final ApiKeyProperties properties;
    private final Clock clock;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ApiKeyProperties properties, Clock clock) {
        this.apiKeyService = apiKeyService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(API_KEY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            authenticateApiKey(request);
            filterChain.doFilter(request, response);
        } catch (ApiUnauthorizedException exception) {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Authentication required\",\"status\":401,\"detail\":\"API key authentication failed\"}");
        }
    }

    private void authenticateApiKey(HttpServletRequest request) {
        String apiKey = required(request, API_KEY_HEADER);
        String timestamp = required(request, TIMESTAMP_HEADER);
        String signature = required(request, SIGNATURE_HEADER);
        Instant signedAt = parseTimestamp(timestamp);
        Duration age = Duration.between(signedAt, Instant.now(clock)).abs();
        if (age.compareTo(properties.signatureTtl()) > 0) {
            throw new ApiUnauthorizedException("API key signature expired");
        }
        ApiKeyService.ApiKeyAuthentication authentication = apiKeyService.authenticate(apiKey, clientIp(request))
            .orElseThrow(() -> new ApiUnauthorizedException("API key rejected"));
        String canonical = canonicalRequest(request, timestamp);
        if (!apiKeyService.validSignature(authentication.secret(), canonical, signature)) {
            throw new ApiUnauthorizedException("API key signature mismatch");
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            authentication.userId().toString(),
            "api-key:" + authentication.keyId(),
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    private String canonicalRequest(HttpServletRequest request, String timestamp) {
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        String bodyHash = request.getHeader(BODY_HASH_HEADER) == null ? "" : request.getHeader(BODY_HASH_HEADER);
        return request.getMethod() + "\n"
            + request.getRequestURI() + "\n"
            + query + "\n"
            + timestamp + "\n"
            + bodyHash;
    }

    private String required(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new ApiUnauthorizedException(header + " is required");
        }
        return value.trim();
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (RuntimeException exception) {
            throw new ApiUnauthorizedException("invalid API key timestamp");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
