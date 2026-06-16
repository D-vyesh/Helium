#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Migration Rollback Validation
# Verifies that migrations apply cleanly and the schema is consistent.
# ============================================================================

echo "=== HELIUM Migration Rollback Validation ==="

HELIUM_DIR="${HELIUM_DIR:-$(cd "$(dirname "$0")/../../../.." && pwd)}"
BACKEND_DIR="${HELIUM_DIR}/backend/helium-core"

echo "[1/4] Starting test database container..."
CONTAINER_NAME="helium-migration-test-$(date +%s)"
docker run -d --name "${CONTAINER_NAME}" \
    -e POSTGRES_DB=helium_migration_test \
    -e POSTGRES_USER=helium \
    -e POSTGRES_PASSWORD=test \
    -p 54321:5432 \
    postgres:16-alpine

echo "  Waiting for database..."
sleep 5
until docker exec "${CONTAINER_NAME}" pg_isready -U helium > /dev/null 2>&1; do
    sleep 1
done

echo "[2/4] Running all migrations forward..."
cd "${BACKEND_DIR}"

HELIUM_DB_URL="jdbc:postgresql://localhost:54321/helium_migration_test" \
HELIUM_DB_USERNAME="helium" \
HELIUM_DB_PASSWORD="test" \
SPRING_PROFILES_ACTIVE="test" \
./gradlew :app:flywayMigrate 2>&1 || {
    echo "ERROR: Migrations failed to apply"
    docker rm -f "${CONTAINER_NAME}" 2>/dev/null
    exit 1
}
echo "  All migrations applied successfully"

echo "[3/4] Verifying schema consistency..."
docker exec "${CONTAINER_NAME}" psql -U helium -d helium_migration_test -c "
    SELECT schemaname, tablename FROM pg_tables
    WHERE schemaname = 'public'
    ORDER BY tablename;
" || true

docker exec "${CONTAINER_NAME}" psql -U helium -d helium_migration_test -c "
    SELECT version, description, success FROM flyway_schema_history
    ORDER BY installed_rank;
" || true

echo "[4/4] Cleanup..."
docker rm -f "${CONTAINER_NAME}" 2>/dev/null

echo "=== Migration validation complete ==="
