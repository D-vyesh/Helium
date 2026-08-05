# Blockchain certification runbook

The blockchain certification tests interact with real local test nodes. They are deliberately excluded from the ordinary backend test run, because a passing result is meaningful only when the dedicated PostgreSQL, Redis, Bitcoin regtest, Anvil, and Solana validator services are running.

## Start the certification dependencies

From the repository root, start only the dependency services:

```powershell
docker compose -f docker-compose.blockchain-certification.yml up -d postgres redis bitcoin anvil solana
```

Wait until every service is healthy:

```powershell
docker compose -f docker-compose.blockchain-certification.yml ps
```

## Run the certification suite

From `backend/helium-core`, explicitly opt in for this shell and run the certification package:

```powershell
$env:HELIUM_RUN_BLOCKCHAIN_CERTIFICATION = "true"
.\gradlew :app:test --tests "com.helium.core.wallet.certification.*" --no-daemon
```

The defaults use local ports 55432 (PostgreSQL), 56379 (Redis), 18443 (Bitcoin regtest), 18545 (Anvil), and 18899 (Solana). Override them with the corresponding `CERT_*` variables when needed.

## Clean up

After collecting the test result, stop the dedicated services:

```powershell
docker compose -f docker-compose.blockchain-certification.yml down
```

Do not treat skipped certification tests as release approval. The release gate requires this opt-in suite to pass on the dedicated environment.
