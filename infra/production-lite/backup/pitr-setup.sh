#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM PostgreSQL Point-in-Time Recovery Setup
# Configures WAL archiving for continuous backup and PITR capability.
# ============================================================================

ARCHIVE_DIR="${ARCHIVE_DIR:-/var/backups/helium/wal-archive}"
POSTGRES_DATA="${POSTGRES_DATA:-/var/lib/postgresql/data}"

echo "=== HELIUM PITR Setup ==="

mkdir -p "${ARCHIVE_DIR}"

echo "[1/3] Configuring WAL archiving..."
cat >> "${POSTGRES_DATA}/postgresql.conf" <<EOF

# ── HELIUM PITR Configuration ──────────────────────────────────────
archive_mode = on
archive_command = 'cp %p ${ARCHIVE_DIR}/%f'
archive_timeout = 300
wal_level = replica
max_wal_senders = 5
wal_keep_size = 1GB
EOF

echo "[2/3] WAL archive directory: ${ARCHIVE_DIR}"
echo "[3/3] PostgreSQL restart required to activate WAL archiving."

echo ""
echo "=== PITR Recovery Instructions ==="
echo ""
echo "To recover to a specific point in time:"
echo ""
echo "1. Stop PostgreSQL"
echo "2. Create recovery.signal file in \${PGDATA}"
echo "3. Add to postgresql.conf:"
echo "   restore_command = 'cp ${ARCHIVE_DIR}/%f %p'"
echo "   recovery_target_time = '2024-01-01 12:00:00+00'"
echo "   recovery_target_action = 'promote'"
echo "4. Start PostgreSQL"
echo ""
echo "=== Setup complete ==="
