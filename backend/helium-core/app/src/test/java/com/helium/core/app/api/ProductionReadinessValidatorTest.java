package com.helium.core.app.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProductionReadinessValidatorTest {

    @Test
    void rejectsDefaultOrMissingProductionConfiguration() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(new MockEnvironment());

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
            .hasMessageContaining("HELIUM_JWT_SECRET")
            .hasMessageContaining("HELIUM_WALLET_CHAIN_MONITOR_ENABLED")
            .hasMessageContaining("HELIUM_BTC_RPC_NODES");
    }

    @Test
    void acceptsCompleteNonPlaceholderProductionConfiguration() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("HELIUM_JWT_SECRET", "a-secure-production-jwt-secret-that-is-long-enough")
            .withProperty("HELIUM_TOTP_ENCRYPTION_KEY", "a-secure-production-totp-key-that-is-long-enough")
            .withProperty("HELIUM_API_KEY_PEPPER", "a-secure-production-api-pepper-that-is-long-enough")
            .withProperty("HELIUM_DB_PASSWORD", "a-secure-production-database-password-that-is-long-enough")
            .withProperty("HELIUM_REDIS_PASSWORD", "a-secure-production-redis-password-that-is-long-enough")
            .withProperty("HELIUM_WALLET_CHAIN_MONITOR_ENABLED", "true")
            .withProperty("HELIUM_WALLET_BUILDER_WORKER_ENABLED", "true")
            .withProperty("HELIUM_CUSTODY_SIGNING_WORKER_ENABLED", "true")
            .withProperty("HELIUM_WALLET_BROADCAST_WORKER_ENABLED", "true")
            .withProperty("HELIUM_WALLET_CONFIRMATION_WORKER_ENABLED", "true")
            .withProperty("HELIUM_BTC_RPC_NODES", "https://btc-primary.example,https://btc-secondary.example")
            .withProperty("HELIUM_ETH_RPC_NODES", "https://eth-primary.example,https://eth-secondary.example")
            .withProperty("HELIUM_SOL_RPC_NODES", "https://sol-primary.example,https://sol-secondary.example")
            .withProperty("HELIUM_SANCTIONS_BASE_URL", "https://sanctions.example")
            .withProperty("HELIUM_SANCTIONS_API_TOKEN", "a-secure-sanctions-api-token-that-is-long-enough");

        ProductionReadinessValidator validator = new ProductionReadinessValidator(environment);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
            .doesNotThrowAnyException();
    }
}
