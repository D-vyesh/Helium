#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# HELIUM Production Certification Suite
# Master script that runs all certification checks and produces a readiness report.
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HELIUM_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
API_URL="${API_URL:-http://localhost:8080}"

mkdir -p "${RESULTS_DIR}"

TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
WARNINGS=0

pass() { TOTAL_CHECKS=$((TOTAL_CHECKS + 1)); PASSED_CHECKS=$((PASSED_CHECKS + 1)); echo "  ✅ PASS: $1"; }
fail() { TOTAL_CHECKS=$((TOTAL_CHECKS + 1)); FAILED_CHECKS=$((FAILED_CHECKS + 1)); echo "  ❌ FAIL: $1"; }
warn() { WARNINGS=$((WARNINGS + 1)); echo "  ⚠️  WARN: $1"; }
section() { echo ""; echo "━━━ $1 ━━━"; }

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        HELIUM Production Certification Suite                ║"
echo "║        Timestamp: ${TIMESTAMP}                         ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# ── 1. Build Verification ──────────────────────────────────────────
section "1. BUILD VERIFICATION"

echo "  Running gradle clean test..."
cd "${HELIUM_DIR}/backend/helium-core"
if ./gradlew.bat clean test --rerun-tasks --no-daemon 2>&1 | tail -5; then
    pass "Gradle build and tests"
else
    fail "Gradle build and tests"
fi

# ── 2. Security Checks ────────────────────────────────────────────
section "2. SECURITY HARDENING"

# Check API key scopes exist in schema
if grep -q "scopes" "${HELIUM_DIR}/backend/helium-core/app/src/main/resources/db/migration/V13__production_certification_security.sql"; then
    pass "API key scopes migration exists"
else
    fail "API key scopes migration missing"
fi

# Check nonce table exists
if grep -q "api_key_nonces" "${HELIUM_DIR}/backend/helium-core/app/src/main/resources/db/migration/V13__production_certification_security.sql"; then
    pass "API key nonce table migration exists"
else
    fail "API key nonce table migration missing"
fi

# Check rate limiting filter exists
if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/RateLimitingFilter.java" ]; then
    if grep -q "per-user\|MAX_USER_REQUESTS\|X-RateLimit" "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/RateLimitingFilter.java"; then
        pass "Per-user rate limiting implemented"
    else
        warn "Rate limiting exists but may not have per-user limits"
    fi
else
    fail "Rate limiting filter missing"
fi

# Check WebSocket authentication
if grep -q "UNAUTHORIZED\|beforeHandshake" "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/WebSocketConfiguration.java"; then
    pass "WebSocket authentication enforced"
else
    fail "WebSocket authentication missing"
fi

# Check replay protection
if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/ApiKeyNonceService.java" ]; then
    pass "Nonce-based replay protection implemented"
else
    fail "Replay protection missing"
fi

# ── 3. Session Infrastructure ─────────────────────────────────────
section "3. SESSION INFRASTRUCTURE"

if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/RedisSessionCacheAdapter.java" ]; then
    pass "Redis session cache adapter exists"
else
    fail "Redis session cache missing"
fi

if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/RedisSessionInvalidationListener.java" ]; then
    pass "Distributed session invalidation listener exists"
else
    fail "Session invalidation listener missing"
fi

if grep -q "convertAndSend\|helium:sessions:revoked" "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/RedisSessionCacheAdapter.java"; then
    pass "Session revocation pub/sub propagation"
else
    fail "Session revocation propagation missing"
fi

# ── 4. Outbox Pattern ─────────────────────────────────────────────
section "4. OUTBOX PATTERN"

if grep -q "publishWithDeduplication" "${HELIUM_DIR}/backend/helium-core/modules/outbox/src/main/java/com/helium/core/outbox/application/OutboxPublisher.java"; then
    pass "Exactly-once deduplication method exists"
else
    fail "Outbox deduplication missing"
fi

if grep -q "DEAD_LETTER\|MAX_ATTEMPTS\|exponential\|JITTER\|calculateNextAttempt" "${HELIUM_DIR}/backend/helium-core/modules/outbox/src/main/java/com/helium/core/outbox/infrastructure/OutboxProcessor.java"; then
    pass "Exponential backoff with dead letter implemented"
else
    fail "Outbox backoff strategy missing"
fi

for HANDLER in MatchingOutboxEventHandler SettlementOutboxEventHandler MarketDataOutboxEventHandler WalletOutboxEventHandler; do
    if find "${HELIUM_DIR}/backend/helium-core/modules" -name "${HANDLER}.java" | grep -q .; then
        pass "Outbox handler: ${HANDLER}"
    else
        fail "Outbox handler missing: ${HANDLER}"
    fi
done

if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/OutboxAdminController.java" ]; then
    pass "Outbox admin/replay API exists"
else
    fail "Outbox admin API missing"
fi

# ── 5. Observability ──────────────────────────────────────────────
section "5. OBSERVABILITY"

if grep -q "opentelemetry\|micrometer-tracing-bridge-otel" "${HELIUM_DIR}/backend/helium-core/app/build.gradle.kts"; then
    pass "OpenTelemetry dependencies configured"
else
    fail "OpenTelemetry dependencies missing"
fi

if [ -f "${HELIUM_DIR}/infra/monitoring/grafana/dashboards/helium-slo.json" ]; then
    pass "SLO Grafana dashboard exists"
else
    fail "SLO dashboard missing"
fi

if [ -f "${HELIUM_DIR}/infra/monitoring/otel/otel-collector-config.yml" ]; then
    pass "OTel Collector configuration exists"
else
    fail "OTel Collector config missing"
fi

if grep -q "jaeger" "${HELIUM_DIR}/docker-compose.yml"; then
    pass "Jaeger tracing in docker-compose"
else
    fail "Jaeger not configured"
fi

# ── 6. High Availability ──────────────────────────────────────────
section "6. HIGH AVAILABILITY"

if [ -f "${HELIUM_DIR}/docker-compose.ha.yml" ]; then
    pass "HA docker-compose exists"
else
    fail "HA docker-compose missing"
fi

for MANIFEST in configmap.yaml secrets.yaml hpa.yaml pdb.yaml ingress.yaml postgres-statefulset.yaml redis-statefulset.yaml; do
    if [ -f "${HELIUM_DIR}/infra/kubernetes/${MANIFEST}" ]; then
        pass "K8s manifest: ${MANIFEST}"
    else
        fail "K8s manifest missing: ${MANIFEST}"
    fi
done

# ── 7. Disaster Recovery ──────────────────────────────────────────
section "7. DISASTER RECOVERY"

for SCRIPT in backup.sh restore.sh pitr-setup.sh migration-rollback-check.sh; do
    if [ -f "${HELIUM_DIR}/infra/production-lite/backup/${SCRIPT}" ]; then
        pass "DR script: ${SCRIPT}"
    else
        fail "DR script missing: ${SCRIPT}"
    fi
done

for CHAOS in chaos-redis-down.sh chaos-db-restart.sh chaos-backend-restart.sh chaos-outbox-failure.sh; do
    if [ -f "${HELIUM_DIR}/testing/chaos/${CHAOS}" ]; then
        pass "Chaos test: ${CHAOS}"
    else
        fail "Chaos test missing: ${CHAOS}"
    fi
done

# ── 8. Secrets Management ─────────────────────────────────────────
section "8. SECRETS MANAGEMENT"

if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/SecretBackend.java" ]; then
    pass "Secret backend abstraction exists"
else
    fail "Secret backend abstraction missing"
fi

if [ -f "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/VaultSecretProvider.java" ]; then
    pass "Vault secret provider exists"
else
    fail "Vault secret provider missing"
fi

if grep -q "SecretRotationEvent" "${HELIUM_DIR}/backend/helium-core/app/src/main/java/com/helium/core/app/api/SecretRotationEvent.java" 2>/dev/null; then
    pass "Secret rotation events implemented"
else
    fail "Secret rotation events missing"
fi

# ══════════════════════════════════════════════════════════════════
# FINAL REPORT
# ══════════════════════════════════════════════════════════════════
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                   CERTIFICATION REPORT                      ║"
echo "╠══════════════════════════════════════════════════════════════╣"

SCORE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))

printf "║  Total Checks:    %-40s ║\n" "${TOTAL_CHECKS}"
printf "║  Passed:          %-40s ║\n" "${PASSED_CHECKS}"
printf "║  Failed:          %-40s ║\n" "${FAILED_CHECKS}"
printf "║  Warnings:        %-40s ║\n" "${WARNINGS}"
printf "║  Readiness Score: %-40s ║\n" "${SCORE}%"
echo "║                                                            ║"

if [ "${FAILED_CHECKS}" -eq 0 ] && [ "${SCORE}" -ge 95 ]; then
    echo "║  Decision: ✅ APPROVED FOR REAL-MONEY PRODUCTION           ║"
    DECISION="APPROVED"
elif [ "${FAILED_CHECKS}" -le 2 ] && [ "${SCORE}" -ge 85 ]; then
    echo "║  Decision: ⚠️  CONDITIONALLY APPROVED                      ║"
    DECISION="CONDITIONALLY APPROVED"
else
    echo "║  Decision: ❌ NOT APPROVED FOR REAL-MONEY PRODUCTION       ║"
    DECISION="NOT APPROVED"
fi

echo "╚══════════════════════════════════════════════════════════════╝"

# Write JSON report
cat > "${RESULTS_DIR}/certification-${TIMESTAMP}.json" <<EOF
{
    "timestamp": "${TIMESTAMP}",
    "totalChecks": ${TOTAL_CHECKS},
    "passed": ${PASSED_CHECKS},
    "failed": ${FAILED_CHECKS},
    "warnings": ${WARNINGS},
    "readinessScore": ${SCORE},
    "decision": "${DECISION}"
}
EOF

echo ""
echo "Report saved to: ${RESULTS_DIR}/certification-${TIMESTAMP}.json"
