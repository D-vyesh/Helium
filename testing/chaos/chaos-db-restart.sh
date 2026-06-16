#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Chaos Test: Database Restart
# Verifies connection pool recovery after PostgreSQL restart.
# ============================================================================

echo "=== HELIUM Chaos Test: Database Restart ==="

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
API_URL="${API_URL:-http://localhost:8080}"

echo "[1/5] Verifying system is healthy..."
curl -sf "${API_URL}/actuator/health" | grep -q "UP" || { echo "ERROR: System is not healthy"; exit 1; }
echo "  System healthy"

echo "[2/5] Stopping PostgreSQL..."
docker compose -f "${COMPOSE_FILE}" stop postgres 2>/dev/null || docker compose -f "${COMPOSE_FILE}" stop postgres-primary 2>/dev/null
sleep 3

echo "[3/5] Testing during outage..."
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/actuator/health" 2>/dev/null || echo "000")
echo "  Health endpoint during outage: HTTP ${HEALTH_STATUS}"
if [ "${HEALTH_STATUS}" = "503" ] || [ "${HEALTH_STATUS}" = "200" ]; then
    echo "  Backend responding gracefully during DB outage"
else
    echo "  WARNING: Unexpected response during DB outage"
fi

echo "[4/5] Restarting PostgreSQL..."
docker compose -f "${COMPOSE_FILE}" start postgres 2>/dev/null || docker compose -f "${COMPOSE_FILE}" start postgres-primary 2>/dev/null
echo "  Waiting for DB to become ready..."
sleep 15

echo "[5/5] Verifying recovery..."
MAX_RETRIES=10
RETRY=0
while [ $RETRY -lt $MAX_RETRIES ]; do
    if curl -sf "${API_URL}/actuator/health" | grep -q "UP"; then
        echo "  System recovered after $((RETRY + 1)) health checks"
        break
    fi
    RETRY=$((RETRY + 1))
    sleep 5
done

if [ $RETRY -eq $MAX_RETRIES ]; then
    echo "  ERROR: System did not recover within timeout"
    exit 1
fi

echo "=== Chaos test complete: Database Restart ==="
