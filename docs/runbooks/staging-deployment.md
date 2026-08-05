# Safe staging deployment

This runbook creates a **localhost-only**, Docker-based staging environment.
It is intentionally unable to send email, consume live market data, or move
assets: all wallet workers are forced off by `docker-compose.staging.yml`.

It is not a production deployment. Do not expose its ports publicly, attach
production credentials, or enable custody workers here.

## 1. Create staging-only configuration

In PowerShell from the repository root:

```powershell
Copy-Item infra/staging/.env.example infra/staging/.env.staging
```

Replace the five `replace-with-...` values in `.env.staging` with distinct,
random staging-only values. Never copy `.env.staging` into the repository and
never reuse a production database, Redis instance, API credential, wallet, or
secret.

## 2. Validate the rendered deployment

```powershell
docker compose --env-file infra/staging/.env.staging -f docker-compose.yml -f docker-compose.staging.yml config
```

Confirm that every published port begins with `127.0.0.1` and that the backend
has `SPRING_PROFILES_ACTIVE: staging` and all wallet worker values set to
`false`.

## 3. Start staging

```powershell
docker compose --env-file infra/staging/.env.staging -f docker-compose.yml -f docker-compose.staging.yml up --build -d
docker compose --env-file infra/staging/.env.staging -f docker-compose.yml -f docker-compose.staging.yml ps
```

The API is available at `http://localhost:18080` and the web app at
`http://localhost:13000` by default.

## 4. Validate it

```powershell
Invoke-RestMethod http://localhost:18080/actuator/health/readiness
Invoke-RestMethod http://localhost:18080/actuator/health/liveness
```

Both checks must return an `UP` status. Then exercise sign-in, trading, admin
freeze controls, and audit visibility using only staging accounts. Confirm that
no wallet worker is running and no external email or market-data connection is
made.

## 5. Stop it

```powershell
docker compose --env-file infra/staging/.env.staging -f docker-compose.yml -f docker-compose.staging.yml down
```

This removes the temporary containers and network while retaining the named
staging database volumes. Add `--volumes` only when you explicitly want to
erase all staging data.

For an internet-facing, production-equivalent deployment, follow
[the production release gate](production-release.md) and use managed services,
a secret manager, real custody controls, independent RPC providers, and an
authorized release approval.
