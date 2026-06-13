# Bootstrap

## Local Dependencies

```text
cd infra/local
docker compose up -d postgres
docker compose --profile redis up -d
```

## Backend

```text
cd backend/helium-core
gradle bootRun
```

## Frontend

```text
npm install
npm run web:dev
```

The repository currently contains no business logic, APIs, domain entities, or database tables.

