# Production Lite

Operational tooling for the single-node "production lite" deployment (`docker-compose.yml` at the repo root).

## Contents

- `backup/backup.sh` — timestamped `pg_dump` + Redis backup with 30-day rotation
- `backup/restore.sh` — restore a backup into the running stack
- `backup/pitr-setup.sh` — configure Postgres point-in-time recovery (WAL archiving)
- `backup/migration-rollback-check.sh` — verify a Flyway migration can be rolled back before applying it

Monitoring for this deployment lives in `infra/monitoring/` (Prometheus, Grafana dashboards, OTel collector) and is enabled via `docker compose --profile monitoring up`. SLOs and runbooks are under `infra/sre/`.
