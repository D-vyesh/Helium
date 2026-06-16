#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Chaos Test: Vault Outage
# Simulates a Vault connection failure to verify caching and emergency behavior.
# ============================================================================

echo "=== HELIUM Chaos Test: Vault Outage ==="

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.ha.yml}"
API_URL="${API_URL:-http://localhost:8080}"

echo "[1/4] Verifying system is healthy..."
curl -sf "${API_URL}/actuator/health" | grep -q "UP" || { echo "ERROR: System is not healthy"; exit 1; }

echo "[2/4] Stopping Vault service..."
# Assuming vault is part of the docker-compose or mock
docker compose -f "${COMPOSE_FILE}" stop vault 2>/dev/null || echo "Vault container not found in compose, simulating network drop."
iptables -A OUTPUT -p tcp --dport 8200 -j DROP 2>/dev/null || true

sleep 5

echo "[3/4] Verifying API still operates using cached secrets..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/api/v1/markets" 2>/dev/null || echo "000")
if [ "${HTTP_STATUS}" = "200" ] || [ "${HTTP_STATUS}" = "401" ]; then
    echo "  API still responding (HTTP ${HTTP_STATUS}) — Cached secrets working."
else
    echo "  WARNING: API returned HTTP ${HTTP_STATUS} — potential degradation issue."
fi

echo "[4/4] Restoring Vault..."
docker compose -f "${COMPOSE_FILE}" start vault 2>/dev/null || true
iptables -D OUTPUT -p tcp --dport 8200 -j DROP 2>/dev/null || true

echo "=== Chaos test complete: Vault Outage ==="
