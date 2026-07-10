# HELIUM

A full-stack cryptocurrency exchange: Spring Boot modular monolith backend, Next.js web frontend, and production-grade infrastructure configuration.

## Repository layout

| Path | What it is |
|---|---|
| `backend/helium-core` | Spring Boot 3.5 / Java 21 modular monolith — ledger, auth, wallet, trading, matching, market data, admin, audit, outbox ([details](backend/helium-core/README.md)) |
| `apps/web` | Next.js 15 / React 19 exchange UI — auth, markets, trade, wallet, orders, admin |
| `infra/` | nginx HA config, Kubernetes manifests (incl. Argo Rollouts canary), Prometheus/Grafana/OTel monitoring, backup & PITR scripts, SRE runbooks |
| `testing/` | k6 load tests (orders, settlements, websocket, failover, replay), chaos scripts, certification runs, multi-chain compose (bitcoind regtest, Ganache, solana-test-validator) |
| `docs/` | Coding standards, module dependency rules, MVP bootstrap notes |
| `db/` | Reserved for future cross-service migrations — the live Flyway migrations are in `backend/helium-core/app/src/main/resources/db/migration/` (V1–V17) |

## Quick start (Docker)

```bash
cp .env.example .env          # adjust secrets before anything non-local
docker compose up --build     # postgres + redis + backend :8080 + frontend :3000
```

Optional observability stack (Prometheus :9090, Grafana :3001, Jaeger :16686):

```bash
docker compose --profile monitoring up
```

`docker-compose.ha.yml` runs the high-availability variant (Postgres streaming replica, Redis Sentinel, 3 backend replicas behind nginx). `docker-compose.enterprise.yml` is an aspirational multi-region stub and is not runnable as-is.

## Local development

Backend (requires JDK 21; Postgres + Redis via `infra/local/compose.yaml`):

```bash
cd backend/helium-core
./gradlew :app:bootRun          # http://localhost:8080
./gradlew test                  # unit + Testcontainers integration tests
```

Frontend (requires Node 22; npm workspace rooted here):

```bash
npm ci
npm run web:dev                 # http://localhost:3000
```

Set `NEXT_PUBLIC_HELIUM_API_BASE_URL` (see `apps/web/.env.example`) so the web app talks to the backend.

## CI/CD

- **CI** (`.github/workflows/ci.yml`): backend test suite, frontend lint/typecheck/build, compose validation — on every PR and push to `main`.
- **CD** (`.github/workflows/cd.yml`): builds and pushes `ghcr.io/d-vyesh/helium-backend` and `ghcr.io/d-vyesh/helium-frontend` images on push to `main` and version tags (`v*`).
