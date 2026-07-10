# HELIUM Core

Spring Boot 3.5 / Java 21 modular monolith powering the HELIUM exchange. Built with Gradle 8.5 as one deployable app (`:app`) composed of bounded-context modules with enforced package boundaries (see `docs/module-boundaries/dependency-rules.md`).

## Modules

| Module | Responsibility |
|---|---|
| `ledger` | Double-entry ledger: accounts, postings, balance snapshots — the financial source of truth |
| `auth-user` | Registration, login, sessions, token refresh, BCrypt password hashing, roles |
| `wallet` | Deposits/withdrawals, HD address generation, BTC/ETH/SOL chain providers and monitors, withdrawal approval workflow, reconciliation |
| `trading` | Order intake, validation, fee handling, fund reservation, fail-closed settlement |
| `matching` | Order book and matching engine |
| `market-data` | Tickers, candles, public trades, sequenced market events |
| `admin` | Reconciliation reports, market controls, SRE/chaos tooling |
| `audit` | Audit trail of privileged actions |
| `outbox` | Transactional outbox for reliable event publication (no external broker) |
| `compliance-lite` | Lightweight compliance checks |
| `shared/*` | Cross-cutting: common, security, persistence, observability, test-support |

## Database

Flyway migrations `V1`–`V17` live in `app/src/main/resources/db/migration/` and cover the full schema, including financial constraints added during trading/settlement hardening. PostgreSQL 16 is the only supported database; Redis is used for infrastructure concerns (see `application.yml` profiles: `local`, `test`, `staging`, `production`).

## Build & test

```bash
./gradlew :app:bootRun    # run locally (needs Postgres + Redis, see infra/local/compose.yaml)
./gradlew test            # unit tests + Postgres/Redis Testcontainers integration suites
./gradlew :app:bootJar    # deployable jar (what the Dockerfile builds)
```

Integration tests (`app/src/test/java`) exercise each module against real Postgres containers — ledger postings, trading settlement, matching, wallet flows, auth, market data, reconciliation, outbox, and the API gateway.

## Configuration

All runtime configuration is environment-driven — see `.env.example` at the repo root for the `HELIUM_*` variables (database, Redis, API-key pepper, outbox, tracing).
