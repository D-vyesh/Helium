# Production release gate

HELIUM must not be launched with a repository default, a `CHANGE-ME` value, or a mocked certification result. The application now enforces these checks whenever the `production` Spring profile is active.

## Required before deploying

- Store independent, 32+ character values for `HELIUM_JWT_SECRET`, `HELIUM_TOTP_ENCRYPTION_KEY`, `HELIUM_API_KEY_PEPPER`, `HELIUM_DB_PASSWORD`, and `HELIUM_REDIS_PASSWORD` in the deployment secret manager.
- Replace `infra/kubernetes/secrets.yaml` with an External Secrets, Sealed Secrets, or Vault CSI integration managed by the deployment environment. Do not commit real values.
- Provide independent production BTC, ETH, and SOL RPC endpoints and set `HELIUM_BTC_RPC_NODES`, `HELIUM_ETH_RPC_NODES`, and `HELIUM_SOL_RPC_NODES`.
- Configure an active custody key and a healthy Vault, HSM, MPC, or offline-custody signing provider. Verify a signature against each supported chain before enabling withdrawals.
- Enable the chain monitor, transaction builder, custody signer, broadcaster, and confirmation workers only after the preceding custody checks pass.
- Configure a sanctions/KYC vendor and obtain legal approval for the jurisdictions in which the exchange will operate. Regulatory filings and external reserve attestations require an authorized compliance officer or auditor; they cannot be generated from repository fixtures.

## Release validation

1. Deploy to an isolated staging environment with `SPRING_PROFILES_ACTIVE=production` and production-equivalent managed services. For a safe local staging rehearsal with asset-moving workers disabled, use [the staging deployment runbook](staging-deployment.md).
2. Confirm startup succeeds only after the production-readiness validator passes.
3. Complete a test deposit, withdrawal, custody signature, broadcast, confirmation, reversal, and reconciliation on each enabled chain.
4. Exercise global API-key revocation, market/withdrawal freeze controls, and recovery before accepting customer deposits.
5. Run the real load, chaos, and blockchain-certification suites against dedicated test infrastructure. Follow [the blockchain certification runbook](blockchain-certification.md); these tests are deliberately opt-in and their mock scripts are not launch evidence.
6. Obtain a signed external reserve attestation before publishing any reserve ratio; the public API only exposes an internally derived liabilities snapshot.
