# HELIUM SLO and SLI Definitions

This document defines the Service Level Objectives (SLOs) and Service Level Indicators (SLIs) for the HELIUM Exchange production environment.

## 1. Trading API Reliability
*   **SLI**: Percentage of HTTP `200` or `201` responses for `POST /api/v1/orders`.
*   **SLO**: 99.99% success rate over a rolling 30-day window.

## 2. API Latency
*   **SLI**: 99th percentile (P99) latency for all `/api/v1/*` requests, measured at the load balancer.
*   **SLO**: P99 < 50ms over a rolling 7-day window.

## 3. Matching Engine Throughput & Latency
*   **SLI**: 99th percentile (P99) duration to process a full matching cycle (order ingested to execution events published).
*   **SLO**: P99 < 10ms.

## 4. Market Data Distribution
*   **SLI**: 95th percentile (P95) latency from execution event generation to WebSocket broadcast fanout completion.
*   **SLO**: P95 < 20ms.

## 5. Settlement Pipeline
*   **SLI**: 95th percentile (P95) lag between trade execution and final settlement confirmation in the ledger.
*   **SLO**: P95 < 3 seconds.

## 6. Outbox Processing
*   **SLI**: Time for an outbox event to transition from `PENDING` to `PROCESSED`.
*   **SLO**: 99% of events processed within 500ms of creation. Max backlog size < 1000 events.

## Measurement and Alerting
Metrics are collected via Micrometer and aggregated in Prometheus. Alerts fire to PagerDuty if burn rates indicate an SLO will be breached within 1 hour or 6 hours.
