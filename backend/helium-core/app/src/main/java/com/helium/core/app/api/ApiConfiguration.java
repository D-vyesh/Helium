package com.helium.core.app.api;

import com.helium.core.authuser.application.SessionPort;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiConfiguration {
    @Bean
    OpenAPI heliumOpenApi() {
        return new OpenAPI().info(new Info()
            .title("HELIUM API")
            .version("v1")
            .description("Production exchange API gateway — real-money deployment"));
    }

    @Bean
    CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.helium.exchange"));
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "X-Requested-With",
            "X-API-Key", "X-API-Signature", "X-API-Timestamp", "X-API-Nonce", "X-API-Body-SHA256",
            "X-Correlation-ID"
        ));
        configuration.setExposedHeaders(List.of(
            "X-Correlation-ID", "X-RateLimit-Remaining", "X-RateLimit-Reset"
        ));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/ws/**", configuration);
        return new CorsFilter(source);
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
        HttpSecurity http,
        SessionPort sessionPort,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/api/v1/markets/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/dashboard/**", "/wallet/**", "/trade/**").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Admin endpoints require SCOPE_ADMIN
                .requestMatchers("/api/v1/admin/**").hasAnyAuthority("SCOPE_ADMIN", "ROLE_ADMIN")
                // Trading endpoints require SCOPE_TRADE
                .requestMatchers("/api/v1/orders/**", "/api/v1/trade/**").hasAnyAuthority("SCOPE_TRADE", "ROLE_USER")
                // Withdrawal endpoints require SCOPE_WITHDRAW
                .requestMatchers("/api/v1/wallet/**", "/api/v1/wallets/withdraw/**").hasAnyAuthority("SCOPE_WITHDRAW", "ROLE_USER")
                // All other authenticated API endpoints require at least SCOPE_READ
                .requestMatchers("/api/**").hasAnyAuthority("SCOPE_READ", "ROLE_USER")
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> basic.disable())
            .formLogin(login -> login.disable())
            .logout(logout -> logout.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write("{\"type\":\"https://api.helium.exchange/problems/401\",\"title\":\"Authentication required\",\"status\":401,\"detail\":\"authentication is required\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write("{\"type\":\"https://api.helium.exchange/problems/403\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"access is forbidden\"}");
                })
            )
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new SessionAuthenticationFilter(sessionPort), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
