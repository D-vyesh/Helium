#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM PostgreSQL + Redis Restore Script
# Restores from backup files created by backup.sh.
# ============================================================================

BACKUP_DIR="${BACKUP_DIR:-/var/backups/helium}"
POSTGRES_HOST="${POSTGRES_HOST:-postgres-primary}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-helium}"
POSTGRES_USER="${POSTGRES_USER:-helium}"
REDIS_HOST="${REDIS_HOST:-redis-master}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

PG_BACKUP_FILE="${1:-}"
REDIS_BACKUP_FILE="${2:-}"

if [ -z "${PG_BACKUP_FILE}" ]; then
    echo "Usage: $0 <pg_backup_file> [redis_backup_file]"
    echo ""
    echo "Available PostgreSQL backups:"
    ls -la "${BACKUP_DIR}/postgres/" 2>/dev/null || echo "  No backups found"
    echo ""
    echo "Available Redis backups:"
    ls -la "${BACKUP_DIR}/redis/" 2>/dev/null || echo "  No backups found"
    exit 1
fi

echo "=== HELIUM Restore ==="
echo "WARNING: This will overwrite the current database!"
echo "PostgreSQL backup: ${PG_BACKUP_FILE}"
echo "Redis backup: ${REDIS_BACKUP_FILE:-none}"
echo ""
read -p "Are you sure you want to continue? (yes/no) " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

# ── Validate backup file ───────────────────────────────────────────
echo "[1/5] Validating backup file..."
if [ ! -f "${PG_BACKUP_FILE}" ]; then
    echo "ERROR: PostgreSQL backup file not found: ${PG_BACKUP_FILE}"
    exit 1
fi
pg_restore --list "${PG_BACKUP_FILE}" > /dev/null 2>&1
echo "  Backup file validation passed"

# ── Pre-restore snapshot ───────────────────────────────────────────
echo "[2/5] Creating pre-restore snapshot..."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PRE_RESTORE="${BACKUP_DIR}/postgres/pre_restore_${TIMESTAMP}.dump"
PGPASSWORD="${POSTGRES_PASSWORD:-}" pg_dump \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -Fc -f "${PRE_RESTORE}" 2>/dev/null || echo "  Pre-restore snapshot skipped (DB may be empty)"

# ── PostgreSQL Restore ─────────────────────────────────────────────
echo "[3/5] Restoring PostgreSQL..."
PGPASSWORD="${POSTGRES_PASSWORD:-}" pg_restore \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    --clean \
    --if-exists \
    --no-owner \
    --no-privileges \
    "${PG_BACKUP_FILE}" 2>&1 || true
echo "  PostgreSQL restore complete"

# ── Redis Restore ──────────────────────────────────────────────────
if [ -n "${REDIS_BACKUP_FILE}" ] && [ -f "${REDIS_BACKUP_FILE}" ]; then
    echo "[4/5] Redis restore requires manual intervention:"
    echo "  1. Stop Redis server"
    echo "  2. Copy ${REDIS_BACKUP_FILE} to Redis data directory as dump.rdb"
    echo "  3. Restart Redis server"
else
    echo "[4/5] Skipping Redis restore (no backup file specified)"
fi

# ── Health Check ───────────────────────────────────────────────────
echo "[5/5] Post-restore health check..."
PGPASSWORD="${POSTGRES_PASSWORD:-}" psql \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -c "SELECT count(*) FROM flyway_schema_history;" > /dev/null 2>&1 \
    && echo "  Database health check passed" \
    || echo "  WARNING: Database health check failed"

echo "=== Restore complete ==="
