#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Chaos Test: Network Partition
# Simulates a network split between backend and Redis to verify reconnection.
# ============================================================================

echo "=== HELIUM Chaos Test: Network Partition ==="

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.ha.yml}"

echo "[1/3] Disconnecting backend-1 from network..."
docker network disconnect helium_default backend-1 2>/dev/null || true

sleep 10

echo "[2/3] Reconnecting backend-1..."
docker network connect helium_default backend-1 2>/dev/null || true

sleep 15

echo "[3/3] Verifying backend-1 recovery..."
HEALTH_STATUS=$(docker exec backend-1 curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
if [ "${HEALTH_STATUS}" = "200" ]; then
    echo "  backend-1 recovered successfully."
else
    echo "  WARNING: backend-1 failed to recover after network partition."
fi

echo "=== Chaos test complete: Network Partition ==="
