package com.helium.core.app.api;

import com.helium.core.authuser.application.SessionPort;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
            .description("Closed-beta exchange API gateway"));
    }

    @Bean
    CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.helium.exchange"));
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
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
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .httpBasic(basic -> basic.disable())
            .formLogin(login -> login.disable())
            .logout(logout -> logout.disable())
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new SessionAuthenticationFilter(sessionPort), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
