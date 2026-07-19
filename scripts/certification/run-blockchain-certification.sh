#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.blockchain-certification.yml}"
KEEP_RUNNING="${KEEP_RUNNING:-false}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"

cd "$ROOT_DIR"

cert_value() {
  key="$1"
  default_value="$2"
  current_value="$(printenv "$key" 2>/dev/null || true)"
  if [ -n "$current_value" ]; then
    printf '%s' "$current_value"
    return
  fi
  if [ -f ".env" ]; then
    line="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" .env | tail -n 1 || true)"
    if [ -n "$line" ]; then
      value="${line#*=}"
      value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
      value="${value%\"}"
      value="${value#\"}"
      value="${value%\'}"
      value="${value#\'}"
      printf '%s' "$value"
      return
    fi
  fi
  printf '%s' "$default_value"
}

status() {
  printf '%s: %s\n' "$1" "$2"
}

cleanup() {
  if [ "$KEEP_RUNNING" != "true" ]; then
    docker compose -f "$COMPOSE_FILE" down --remove-orphans >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

status "COMPOSE CONFIG" "RUNNING"
docker compose -f "$COMPOSE_FILE" config --quiet >/dev/null
status "COMPOSE CONFIG" "PASS"

status "STACK START" "RUNNING"
docker compose -f "$COMPOSE_FILE" up -d --build
status "STACK START" "PASS"

status "STACK PS" "RUNNING"
docker compose -f "$COMPOSE_FILE" ps

status "BTC RPC" "RUNNING"
BTC_RPC_USER="$(cert_value CERT_BTC_RPC_USER helium)"
BTC_RPC_PASSWORD="$(cert_value CERT_BTC_RPC_PASSWORD helium)"
docker compose -f "$COMPOSE_FILE" exec -T bitcoin bitcoin-cli -regtest -rpcuser="$BTC_RPC_USER" -rpcpassword="$BTC_RPC_PASSWORD" getblockchaininfo >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T bitcoin bitcoin-cli -regtest -rpcuser="$BTC_RPC_USER" -rpcpassword="$BTC_RPC_PASSWORD" getblockcount >/dev/null
status "BTC RPC" "PASS"

status "ETH RPC" "RUNNING"
docker compose -f "$COMPOSE_FILE" exec -T anvil cast chain-id --rpc-url http://127.0.0.1:8545 >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T anvil cast block-number --rpc-url http://127.0.0.1:8545 >/dev/null
status "ETH RPC" "PASS"

status "SOL RPC" "RUNNING"
docker compose -f "$COMPOSE_FILE" exec -T solana solana -u http://127.0.0.1:8899 cluster-version >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T solana solana -u http://127.0.0.1:8899 slot >/dev/null
status "SOL RPC" "PASS"

status "BTC DEPOSIT" "RUNNING"
status "BTC WITHDRAWAL" "RUNNING"
status "ETH DEPOSIT" "RUNNING"
status "ETH WITHDRAWAL" "RUNNING"
status "SOL DEPOSIT" "RUNNING"
status "SOL WITHDRAWAL" "RUNNING"
status "RESTART RECOVERY" "RUNNING"
status "PROVIDER DISAGREEMENT" "RUNNING"
status "REORG HANDLING" "RUNNING"

./backend/helium-core/gradlew -p backend/helium-core :app:test \
  --tests '*BitcoinRegtestLifecycleIntegrationTest' \
  --tests '*EthereumAnvilLifecycleIntegrationTest' \
  --tests '*SolanaValidatorLifecycleIntegrationTest' \
  --tests '*BlockchainRestartRecoveryIntegrationTest' \
  --tests '*BlockchainProviderDisagreementIntegrationTest' \
  --tests '*CanonicalChainReorgIntegrationTest'

status "BTC DEPOSIT" "PASS"
status "BTC WITHDRAWAL" "PASS"
status "ETH DEPOSIT" "PASS"
status "ETH WITHDRAWAL" "PASS"
status "SOL DEPOSIT" "PASS"
status "SOL WITHDRAWAL" "PASS"
status "RESTART RECOVERY" "PASS"
status "PROVIDER DISAGREEMENT" "PASS"
status "REORG HANDLING" "PASS"
status "BLOCKCHAIN CERTIFICATION" "PASSED"
