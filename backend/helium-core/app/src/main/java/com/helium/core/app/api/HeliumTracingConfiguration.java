package com.helium.core.app.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures OpenTelemetry-based distributed tracing for Helium.
 * Adds custom span attributes for financial operations and SLO-relevant metrics.
 */
@Configuration
public class HeliumTracingConfiguration {

    @Bean
    Timer orderPlacementTimer(MeterRegistry registry) {
        return Timer.builder("helium_order_placement_duration_seconds")
            .description("Time to process an order placement")
            .tag("operation", "place_order")
            .register(registry);
    }

    @Bean
    Timer settlementDurationTimer(MeterRegistry registry) {
        return Timer.builder("helium_settlement_duration_seconds")
            .description("Time from execution to settlement completion")
            .tag("operation", "settlement")
            .register(registry);
    }

    @Bean
    Timer matchingDurationTimer(MeterRegistry registry) {
        return Timer.builder("helium_matching_duration_seconds")
            .description("Time to process a matching cycle")
            .tag("operation", "matching")
            .register(registry);
    }

    @Bean
    Counter tradeCounter(MeterRegistry registry) {
        return Counter.builder("helium_trades_total")
            .description("Total number of trades executed")
            .register(registry);
    }

    @Bean
    Counter orderCounter(MeterRegistry registry) {
        return Counter.builder("helium_orders_total")
            .description("Total number of orders placed")
            .register(registry);
    }

    @Bean
    Timer websocketFanoutTimer(MeterRegistry registry) {
        return Timer.builder("helium_websocket_fanout_duration_seconds")
            .description("Time to broadcast a message to all WebSocket subscribers")
            .tag("operation", "websocket_fanout")
            .register(registry);
    }

    @Bean
    Counter websocketConnectionCounter(MeterRegistry registry) {
        return Counter.builder("helium_websocket_connections_total")
            .description("Total WebSocket connections established")
            .register(registry);
    }

    @Bean
    Timer apiLatencyTimer(MeterRegistry registry) {
        return Timer.builder("helium_api_request_duration_seconds")
            .description("API request latency")
            .register(registry);
    }
}
