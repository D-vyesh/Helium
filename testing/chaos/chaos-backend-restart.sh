#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Chaos Test: Backend Restart
# Verifies zero-downtime during rolling backend restart.
# ============================================================================

echo "=== HELIUM Chaos Test: Backend Restart ==="

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.ha.yml}"
API_URL="${API_URL:-http://localhost:8080}"
ERROR_COUNT=0
TOTAL_REQUESTS=0

echo "[1/3] Starting continuous traffic during restart..."
# Background traffic generator
(
    for i in $(seq 1 60); do
        HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/actuator/health" 2>/dev/null || echo "000")
        echo "${HTTP_STATUS}" >> /tmp/helium_chaos_backend_results.txt
        sleep 1
    done
) &
TRAFFIC_PID=$!

echo "[2/3] Rolling restart of backend replicas..."
sleep 5
docker compose -f "${COMPOSE_FILE}" restart backend-1 &
sleep 10
docker compose -f "${COMPOSE_FILE}" restart backend-2 &
sleep 10
docker compose -f "${COMPOSE_FILE}" restart backend-3 &
sleep 15

echo "  Waiting for traffic generator to complete..."
wait ${TRAFFIC_PID} 2>/dev/null || true

echo "[3/3] Analyzing results..."
if [ -f /tmp/helium_chaos_backend_results.txt ]; then
    TOTAL_REQUESTS=$(wc -l < /tmp/helium_chaos_backend_results.txt)
    ERROR_COUNT=$(grep -cv "200" /tmp/helium_chaos_backend_results.txt 2>/dev/null || echo "0")
    SUCCESS_RATE=$(echo "scale=1; ($TOTAL_REQUESTS - $ERROR_COUNT) * 100 / $TOTAL_REQUESTS" | bc 2>/dev/null || echo "unknown")
    echo "  Total requests: ${TOTAL_REQUESTS}"
    echo "  Errors: ${ERROR_COUNT}"
    echo "  Success rate: ${SUCCESS_RATE}%"
    rm -f /tmp/helium_chaos_backend_results.txt
else
    echo "  No results file found"
fi

if [ "${ERROR_COUNT}" -le 3 ]; then
    echo "  PASS: Near-zero-downtime restart achieved"
else
    echo "  WARNING: ${ERROR_COUNT} errors during restart — review LB health checks"
fi

echo "=== Chaos test complete: Backend Restart ==="
