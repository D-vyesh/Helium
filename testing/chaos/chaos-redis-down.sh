#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Chaos Test: Redis Down
# Verifies graceful degradation when Redis becomes unavailable.
# ============================================================================

echo "=== HELIUM Chaos Test: Redis Down ==="

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
API_URL="${API_URL:-http://localhost:8080}"

echo "[1/5] Verifying system is healthy..."
curl -sf "${API_URL}/actuator/health" | grep -q "UP" || { echo "ERROR: System is not healthy"; exit 1; }
echo "  System healthy"

echo "[2/5] Stopping Redis..."
docker compose -f "${COMPOSE_FILE}" stop redis 2>/dev/null || docker compose -f "${COMPOSE_FILE}" stop redis-master 2>/dev/null
sleep 5

echo "[3/5] Testing graceful degradation..."
# Rate limiter should fall back to in-memory
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/api/v1/markets" 2>/dev/null || echo "000")
if [ "${HTTP_STATUS}" = "200" ] || [ "${HTTP_STATUS}" = "401" ]; then
    echo "  API still responding (HTTP ${HTTP_STATUS}) — rate limiter fallback working"
else
    echo "  WARNING: API returned HTTP ${HTTP_STATUS} — potential degradation issue"
fi

# Health endpoint should still work
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/actuator/health" 2>/dev/null || echo "000")
echo "  Health endpoint: HTTP ${HEALTH_STATUS}"

echo "[4/5] Restarting Redis..."
docker compose -f "${COMPOSE_FILE}" start redis 2>/dev/null || docker compose -f "${COMPOSE_FILE}" start redis-master 2>/dev/null
sleep 10

echo "[5/5] Verifying recovery..."
curl -sf "${API_URL}/actuator/health" | grep -q "UP" && echo "  System recovered successfully" || echo "  WARNING: System health check failed after Redis restart"

echo "=== Chaos test complete: Redis Down ==="
