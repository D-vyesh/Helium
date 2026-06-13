package com.helium.core.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.helium.core")
public class HeliumCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeliumCoreApplication.class, args);
    }
}

