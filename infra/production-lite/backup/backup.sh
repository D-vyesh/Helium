#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM PostgreSQL + Redis Backup Script
# Creates timestamped backups with rotation (keeps last 30).
# ============================================================================

BACKUP_DIR="${BACKUP_DIR:-/var/backups/helium}"
POSTGRES_HOST="${POSTGRES_HOST:-postgres-primary}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-helium}"
POSTGRES_USER="${POSTGRES_USER:-helium}"
REDIS_HOST="${REDIS_HOST:-redis-master}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "${BACKUP_DIR}/postgres" "${BACKUP_DIR}/redis"

echo "=== HELIUM Backup: ${TIMESTAMP} ==="

# ── PostgreSQL Backup ──────────────────────────────────────────────
echo "[1/4] Starting PostgreSQL backup..."
PG_BACKUP_FILE="${BACKUP_DIR}/postgres/helium_${TIMESTAMP}.dump"
PGPASSWORD="${POSTGRES_PASSWORD:-}" pg_dump \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -Fc \
    --no-owner \
    --no-privileges \
    -f "${PG_BACKUP_FILE}"

PG_SIZE=$(du -sh "${PG_BACKUP_FILE}" | cut -f1)
echo "  PostgreSQL backup complete: ${PG_BACKUP_FILE} (${PG_SIZE})"

# Validate backup
echo "[2/4] Validating PostgreSQL backup..."
pg_restore --list "${PG_BACKUP_FILE}" > /dev/null 2>&1
echo "  PostgreSQL backup validation passed"

# ── Redis Backup ───────────────────────────────────────────────────
echo "[3/4] Starting Redis backup..."
REDIS_AUTH=""
if [ -n "${REDIS_PASSWORD}" ]; then
    REDIS_AUTH="-a ${REDIS_PASSWORD}"
fi
redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${REDIS_AUTH} BGSAVE
sleep 5

REDIS_BACKUP_FILE="${BACKUP_DIR}/redis/helium_${TIMESTAMP}.rdb"
if redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${REDIS_AUTH} --rdb "${REDIS_BACKUP_FILE}" 2>/dev/null; then
    REDIS_SIZE=$(du -sh "${REDIS_BACKUP_FILE}" | cut -f1)
    echo "  Redis backup complete: ${REDIS_BACKUP_FILE} (${REDIS_SIZE})"
else
    echo "  Redis RDB download not available — skipping RDB backup"
fi

# ── Rotation ───────────────────────────────────────────────────────
echo "[4/4] Rotating old backups (keeping last ${RETENTION_DAYS} days)..."
find "${BACKUP_DIR}/postgres" -name "helium_*.dump" -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true
find "${BACKUP_DIR}/redis" -name "helium_*.rdb" -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true

PG_COUNT=$(find "${BACKUP_DIR}/postgres" -name "helium_*.dump" | wc -l)
REDIS_COUNT=$(find "${BACKUP_DIR}/redis" -name "helium_*.rdb" | wc -l)
echo "  Remaining backups: ${PG_COUNT} PostgreSQL, ${REDIS_COUNT} Redis"

echo "=== Backup complete: ${TIMESTAMP} ==="
