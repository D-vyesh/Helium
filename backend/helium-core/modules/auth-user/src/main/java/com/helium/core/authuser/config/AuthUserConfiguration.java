package com.helium.core.authuser.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthUserConfiguration {
    @Bean
    Clock authUserClock() {
        return Clock.systemUTC();
    }
}
