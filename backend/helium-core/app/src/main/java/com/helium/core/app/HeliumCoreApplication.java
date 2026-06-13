package com.helium.core.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.helium.core")
@EntityScan(basePackages = "com.helium.core")
@EnableJpaRepositories(basePackages = "com.helium.core")
public class HeliumCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeliumCoreApplication.class, args);
    }
}
