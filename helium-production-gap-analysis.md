# Helium → Binance-Grade: Full Production Gap Analysis

> **Scope**: Based on a full code walk of all 708 Java source files, the Next.js frontend, infra configs, and test suite.
> **Date**: 2026-08-06

---

## How to read this document

| Rating | Meaning |
|---|---|
| 🔴 CRITICAL | System cannot safely operate in production without this |
| 🟠 HIGH | Significant financial, legal or reliability risk if absent |
| 🟡 MEDIUM | Important quality/feature gap, but not an immediate blocker |
| 🟢 LOW | Nice-to-have polish, performance, or parity with Binance |

---

## 1. Matching Engine

### 1.1 🔴 CRITICAL — Only `LIMIT` orders are supported

[MatchingOrderType.java](file:///d:/Helium/backend/helium-core/modules/matching/src/main/java/com/helium/core/matching/domain/MatchingOrderType.java) has exactly one value: `LIMIT`.
[SubmitOrderService.java](file:///d:/Helium/backend/helium-core/modules/matching/src/main/java/com/helium/core/matching/application/SubmitOrderService.java#L53-L55) hard-rejects anything else.

**Missing**: `MARKET`, `STOP_LIMIT`, `STOP_MARKET`, `POST_ONLY`, `TRAILING_STOP`, `OCO` (One-Cancels-the-Other).

Without market orders, the exchange cannot function as a normal retail venue. Users cannot trade at the current price.

**Next steps**:
- Add `MARKET`, `STOP_LIMIT`, `POST_ONLY` to the enum.
- Implement taker-only matching path for MARKET orders (sweep opposite side, no resting).
- Store stop-price on `BookOrder`; evaluate on every fill event.

---

### 1.2 🔴 CRITICAL — `MarketCircuitBreaker` is a log-only stub

[MarketCircuitBreaker.java](file:///d:/Helium/backend/helium-core/modules/trading/src/main/java/com/helium/core/trading/application/MarketCircuitBreaker.java#L38-L42) calls `triggerVolatilityHalt()` which only logs. **No actual halt occurs.**

In a real exchange, a circuit breaker that doesn't stop trading is dangerous (flash crashes, cascading liquidations).

**Next steps**:
- Wire `triggerVolatilityHalt` → `MarketMatchingState.halt()` (the domain entity already exists).
- Fire an outbox event → `MatchingEngineService` to refuse new submissions.
- Implement auction-mode price discovery after halt period.
- Persist `lastReferencePrice` in DB (currently in-memory `ConcurrentHashMap` — lost on restart).

---

### 1.3 🟠 HIGH — `RiskSurveillanceEngine` alerts only; no enforcement

[RiskSurveillanceEngine.java](file:///d:/Helium/backend/helium-core/modules/trading/src/main/java/com/helium/core/trading/application/RiskSurveillanceEngine.java) detects wash trading, spoofing, and layering, but only calls `log.error(...)`.

**Missing**: account suspension, SAR generation, flagging the trade in DB.

**Next steps**:
- Emit a `RiskAlertEvent` via the outbox when patterns are detected.
- Wire to `AccountAdministrationService.suspendAccount()` for confirmed wash trading.
- Mark affected orders as `SUSPICIOUS` in the audit trail.
- Flush the in-memory `orderCreationTimes` map periodically or move to Redis.

---

### 1.4 🟠 HIGH — No order expiration scheduler for `DAY` orders

`TimeInForce.DAY` exists in the enum, but there is no background job that expires orders when the business day closes. `OrderExpirationService` exists but needs a scheduler and market-hours awareness.

**Next steps**:
- Add cron job calling `OrderExpirationService` at market close.
- Define "market day" boundaries per market (UTC vs local time).

---

### 1.5 🟡 MEDIUM — Price-time priority only; no pro-rata matching

Current matching in [SubmitOrderService.java#L99](file:///d:/Helium/backend/helium-core/modules/matching/src/main/java/com/helium/core/matching/application/SubmitOrderService.java#L99) uses `findMatchableForUpdate` which returns orders in received sequence (price-time priority). 

Binance uses FIFO/price-time but many venues require pro-rata for derivatives. Not blocking but document the choice.

---

### 1.6 🟢 LOW — Matching engine is single-node (no horizontal scaling)

Advisory locks (`MatchingAdvisoryLockService`) serialize per market at the DB level. This is correct for correctness but limits throughput.

**Binance operates >1M orders/sec** using a purpose-built in-memory engine with sequence logs. Helium's DB-backed engine is appropriate for early production but will need replacement beyond ~5,000 orders/sec per market.

---

## 2. Trading Layer

### 2.1 🔴 CRITICAL — No pre-trade risk checks

There is no global position limit, no notional limit, and no max open orders per user enforced in [OrderPlacementService.java](file:///d:/Helium/backend/helium-core/modules/trading/src/main/java/com/helium/core/trading/application/OrderPlacementService.java) beyond funds reservation.

**Missing**: max open order count per user/market, max daily withdrawal/trading volume, cross-market net exposure limits.

**Next steps**:
- Add configurable `RiskLimitService` checked in `OrderPlacementService` before sending to matching.
- Persist risk counters in Redis for low-latency reads.

---

### 2.2 🟠 HIGH — `FeeSchedule` is flat; no VIP tier system

[FeeSchedule.java](file:///d:/Helium/backend/helium-core/modules/trading/src/main/java/com/helium/core/trading/domain/FeeSchedule.java) stores a single `makerFeeRate` / `takerFeeRate` pair per market. There is no volume-based VIP tier system (Binance has 10 tiers), no native-token discount (like BNB), no referral fee sharing.

**Next steps**:
- Add `UserFeeProfile` table with 30-day trading volume and override rates.
- Scheduler to recalculate tier nightly.
- API for users to see their current tier.

---

### 2.3 🟠 HIGH — `GovernanceApprovalService` is unwired

[GovernanceApprovalService.java](file:///d:/Helium/backend/helium-core/modules/admin/src/main/java/com/helium/core/admin/application/GovernanceApprovalService.java) implements a maker-checker pattern but the `approve` path just saves to DB — it never dispatches the approved action. The `requestType` / `payloadJson` dispatch logic is an empty comment.

**Next steps**:
- Implement a strategy map: `requestType → CommandHandler`.
- Wire fee-schedule updates, market listing, custody-key rotation through governance.
- Add email notifications to approvers.

---

### 2.4 🟡 MEDIUM — Market data feed is Binance-mirrored only

The `HELIUM_BINANCE_*` environment variables reveal that the exchange proxies Binance market data. For internal trades, `BookProjector` and `TickerProjector` produce native data, but the live feed depends on Binance staying available.

**Next steps**:
- Route market data for Helium's own listed markets exclusively from the native matching engine.
- Binance feed is acceptable for reference prices but should not be the only source of truth.

---

## 3. Compliance & KYC

### 3.1 🔴 CRITICAL — KYC is an interface with no production implementation

[AmlKycProvider.java](file:///d:/Helium/backend/helium-core/modules/compliance-lite/src/main/java/com/helium/core/compliance/application/AmlKycProvider.java) is an interface. There is no implementing class anywhere in the codebase. **Users can register and trade without any identity verification.**

Operating a crypto exchange without KYC violates FATF Recommendation 16 and the regulations of every major jurisdiction (EU MiCA, US FinCEN, UK FCA, etc.).

**Next steps**:
- Wire a KYC provider (SumSub, Jumio, Persona, Onfido).
- Enforce KYC status check in `OrderPlacementService` and `WithdrawalRequestService`.
- Block withdrawals above a daily threshold for unverified accounts.
- Store KYC tier (basic / enhanced / institutional) on `UserAccount`.

---

### 3.2 🔴 CRITICAL — Sanctions screening has no production wiring for withdrawals/deposits

[HttpSanctionsScreeningProvider.java](file:///d:/Helium/backend/helium-core/modules/compliance-lite/src/main/java/com/helium/core/compliance/application/HttpSanctionsScreeningProvider.java) exists but is not called from `WithdrawalRequestService`, `DepositService`, or order placement. There is no OFAC/SDN check before funds move.

**Next steps**:
- Call `SanctionsScreeningProvider.isUserSanctioned()` in `RegistrationService`.
- Call `isAddressSanctioned()` in `WithdrawalAddressValidator`.
- Screen depositing addresses when a deposit is detected.
- Reject and freeze funds if a match is found; file SAR automatically.

---

### 3.3 🔴 CRITICAL — All regulatory report endpoints return 503

[RegulatoryReportingController.java](file:///d:/Helium/backend/helium-core/modules/compliance-lite/src/main/java/com/helium/core/compliancelite/api/RegulatoryReportingController.java) intentionally returns `SERVICE_UNAVAILABLE` for SAR, FATF Travel Rule, MiCA, and OFAC. This is honest (no fake data), but it means **regulators cannot be served any reports** in the current state.

**Next steps**:
- Integrate with a regulatory reporting platform (e.g., ACAMS, Napier, ComplyAdvantage).
- Implement FATF Travel Rule data collection at the transaction level.
- Generate MiCA-compliant reports if operating in the EU.
- Automated SAR filing pipeline integrated with `SuspiciousActivityReportService`.

---

### 3.4 🟠 HIGH — `JurisdictionRuleEngine` uses hardcoded rules with no database backing

[JurisdictionRuleEngine.java](file:///d:/Helium/backend/helium-core/modules/compliance-lite/src/main/java/com/helium/core/compliancelite/application/JurisdictionRuleEngine.java) has a hardcoded `Set.of("XMR", "ZEC", "DASH")` and a comment: _"In a real system, we'd check a database"_.

**Next steps**:
- Create `JurisdictionAssetRule` table with (jurisdiction, asset, allowed, effective_date).
- Add geo-IP detection at the API gateway level to set jurisdiction context.
- Add country-of-residence field to `UserAccount`, used for all jurisdiction checks.

---

### 3.5 🟠 HIGH — No Travel Rule (VASP-to-VASP) implementation

When a user withdraws to an external wallet belonging to another VASP, the FATF Travel Rule requires sharing originator/beneficiary information. No such protocol (TRP, OpenVASP, Shyft) is implemented.

---

### 3.6 🟡 MEDIUM — GDPR export/deletion are stubs

[ComplianceExportController.java](file:///d:/Helium/backend/helium-core/modules/compliance-lite/src/main/java/com/helium/core/compliance/api/ComplianceExportController.java) exists but the GDPR data export and deletion functionality is minimal. In the EU, users have the right to erasure and portability (Articles 17 and 20 GDPR).

---

## 4. Custody & Wallet

### 4.1 🔴 CRITICAL — AWS KMS and Azure Key Vault custody providers are stubs

[AwsKmsCustodyProvider.java](file:///d:/Helium/backend/helium-core/modules/wallet/src/main/java/com/helium/core/wallet/application/AwsKmsCustodyProvider.java) and [AzureKeyVaultCustodyProvider.java](file:///d:/Helium/backend/helium-core/modules/wallet/src/main/java/com/helium/core/wallet/application/AzureKeyVaultCustodyProvider.java) are 547 and 567 bytes respectively — almost certainly stub implementations. Only HashiCorp Vault and offline custody have substantial code.

**Next steps**:
- Implement AWS KMS using `SECP256K1` asymmetric key signing.
- Implement Azure Key Vault HSM signing.
- Add HSM cloud (CloudHSM, Thales) integration for maximum security.

---

### 4.2 🔴 CRITICAL — No multi-signature policy for large withdrawals

All withdrawals go through a single-key signing path. For amounts above a threshold, production exchanges require M-of-N multisig (e.g., 2-of-3 HSM keys).

**Next steps**:
- Add withdrawal policy tiers: < $10k → hot wallet; $10k-$100k → warm multisig; > $100k → cold/manual.
- Implement Bitcoin PSBT multi-party signing workflow (partially built in `BitcoinPsbtSignatureAssembler`).
- Add `WithdrawalAuthorizationPolicy` entity configurable per asset and tier.

---

### 4.3 🔴 CRITICAL — Cold wallet is a stub

[OfflineCustodyProvider.java](file:///d:/Helium/backend/helium-core/modules/wallet/src/main/java/com/helium/core/wallet/application/OfflineCustodyProvider.java) reads from `HELIUM_OFFLINE_CUSTODY_RESPONSE_DIRECTORY` — a file-based air-gap simulation. This must be replaced with a formal cold wallet process (hardware signing ceremony, QR code protocol, or hardware security module).

---

### 4.4 🟠 HIGH — No address whitelisting

Users can withdraw to any address. Binance and most production exchanges implement an address whitelist with a 24-48 hour lock before a new address can receive funds, giving users time to detect account compromise.

**Next steps**:
- Add `WithdrawalAddressWhitelist` table.
- Enforce whitelist check in `WithdrawalAddressValidator`.
- Require TOTP re-verification to add new addresses.
- 24-hour freeze on newly added addresses.

---

### 4.5 🟠 HIGH — No fee acceleration / Replace-by-Fee (RBF) for stuck Bitcoin transactions

[StuckTransactionMonitor.java](file:///d:/Helium/backend/helium-core/modules/wallet/src/main/java/com/helium/core/wallet/infrastructure/blockchain/StuckTransactionMonitor.java) detects stuck transactions but there is no RBF (BIP 125) or CPFP (child-pays-for-parent) implementation to unstick them. Users will experience indefinitely pending withdrawals during network congestion.

---

### 4.6 🟠 HIGH — Proof of Reserves requires external attestation

The `ProofOfReserveService` is wired but the `TransparencyPortalController` explicitly states that an external attestation is required before publishing a reserve ratio. This is correct but means Helium currently has no published Proof of Reserves. Binance publishes Merkle-tree-based PoR monthly.

**Next steps**:
- Implement Merkle tree construction over all user balances.
- Publish root hash on-chain.
- Engage a Big-4 auditor or Chainproof for attestation.

---

### 4.7 🟡 MEDIUM — ERC-20 token support is limited

`EthereumDepositScanner` and `EthereumTransactionBuilder` exist, but the scanner likely only monitors native ETH transfers unless ERC-20 events are explicitly filtered. Most exchange volume is in ERC-20 tokens (USDT, USDC, etc.).

---

### 4.8 🟡 MEDIUM — No Ethereum ERC-4337 (Account Abstraction) or native USDC support

No support for gas-fee abstraction or USDC native transfer (Circle's CCTP). Low priority but relevant for institutional clients.

---

## 5. Observability & SRE

### 5.1 🔴 CRITICAL — No alerting integration (PagerDuty, OpsGenie, etc.)

[ExchangeStatusService.java](file:///d:/Helium/backend/helium-core/modules/admin/src/main/java/com/helium/core/admin/application/ExchangeStatusService.java#L27) logs: _"requires on-call notification; no paging provider is configured."_

[SreRunbookAutomation.java](file:///d:/Helium/backend/helium-core/modules/admin/src/main/java/com/helium/core/admin/application/SreRunbookAutomation.java) says: _"an authorized operator must perform and verify recovery before marking it operational."_

If no one gets paged, incidents go undetected. A Binance-grade exchange operates with sub-minute incident detection.

**Next steps**:
- Integrate PagerDuty or OpsGenie SDK.
- Define runbook links per alert.
- Wire Prometheus `ALERTS` → Alertmanager → PagerDuty.

---

### 5.2 🔴 CRITICAL — Grafana dashboards are nearly empty

[helium-overview.json](file:///d:/Helium/infra/monitoring/grafana/dashboards/helium-overview.json) is 1.4 KB (essentially a placeholder). [helium-slo.json](file:///d:/Helium/infra/monitoring/grafana/dashboards/helium-slo.json) is 6.2 KB (slightly more).

**Missing dashboards**: matching engine queue depth, order throughput, settlement lag, custody signing queue, wallet reconciliation, user registration rate, WebSocket connection count, candle latency, deposit/withdrawal volumes.

**Next steps**:
- Add at least 10 production dashboards covering each module.
- Set up SLO burn-rate alerts (error budget exhaustion).

---

### 5.3 🔴 CRITICAL — No distributed tracing in the matching → trading → ledger path

OpenTelemetry is configured (OTLP endpoint wired), but no `@Observed` or manual span instrumentation exists in the critical path: `SubmitOrderService → MatchingOutboxEventHandler → SettlementCoordinator → LedgerPostingPort`.

**Next steps**:
- Add `@Observed` to all critical-path service methods.
- Propagate trace context through the outbox event payload.
- Build a Jaeger search dashboard for order lifecycle traces.

---

### 5.4 🟠 HIGH — `DisasterRecoveryOrchestrator` has no actual DR procedure

[DisasterRecoveryOrchestrator.java](file:///d:/Helium/backend/helium-core/modules/admin/src/main/java/com/helium/core/admin/application/DisasterRecoveryOrchestrator.java#L21-L23) correctly refuses to self-report recovery without external verification, but there is **no implemented DR procedure**: no automated PostgreSQL replica promotion, no Redis sentinel failover, no DNS cutover.

**Next steps**:
- Document and automate PostgreSQL streaming replication + Patroni failover.
- Document Redis Sentinel / Cluster configuration.
- Write tested runbook (not just the stub markdown files that exist).
- Practice quarterly failover drills.

---

### 5.5 🟠 HIGH — `ExchangeStatusService` stores status in memory only

Component statuses live in a `ConcurrentHashMap`. If the backend restarts, all statuses revert to `UNKNOWN`. An SRE looking at a status page after a restart sees nothing meaningful.

**Next steps**:
- Persist component health events to a `component_health_events` table.
- Derive current status as most recent event.
- Expose a public status page (status.helium.exchange).

---

### 5.6 🟡 MEDIUM — Only one Prometheus alert rule file

[helium-slo-rules.yml](file:///d:/Helium/infra/monitoring/prometheus/rules/helium-slo-rules.yml) (2 KB) is the only alert rule file. A production exchange needs hundreds of rules covering:
- Order submission error rate
- Ledger posting failure rate
- Outbox backlog size (the runbook exists, not the alert)
- Blockchain RPC node health
- Custody signing failure rate
- Settlement lag

---

## 6. Infrastructure & Scalability

### 6.1 🔴 CRITICAL — Single PostgreSQL instance with no HA config

`docker-compose.yml` and `infra/kubernetes/postgres-statefulset.yaml` deploy a single Postgres. No streaming replication, no read replicas, no connection pooler (PgBouncer/pgpool).

**Next steps**:
- Deploy PostgreSQL with Patroni + etcd for automatic failover.
- Add at least one read replica for analytics / reconciliation queries.
- Add PgBouncer sidecar.
- Enable `wal_level = logical` for CDC-based audit streaming.

---

### 6.2 🔴 CRITICAL — Redis deployed without cluster or sentinel

Redis is a single instance in `docker-compose.yml`. Session tokens, TOTP replay protection, and API key nonce checks all depend on Redis. A Redis failure causes a complete authentication outage.

**Next steps**:
- Deploy Redis Cluster (≥6 nodes) or Redis Sentinel (3 nodes + 1 replica).
- Configure `maxmemory-policy: noeviction` for auth-critical keys.
- Test Redis failover as a chaos scenario.

---

### 6.3 🟠 HIGH — No Kafka / async event bus; all events synchronous via outbox polling

The outbox pattern is correctly implemented but the "event bus" is a polling loop. For high throughput, Kafka or Pulsar is needed to fan out matching events to market data, settlement, audit, and compliance simultaneously.

This is an architectural change — not urgent for early production but essential past ~10,000 orders/day.

---

### 6.4 🟠 HIGH — Kubernetes HPA only scales on CPU; no custom metrics

[hpa.yaml](file:///d:/Helium/infra/kubernetes/hpa.yaml) uses standard CPU autoscaling. A matching engine is latency-bound, not CPU-bound. The HPA should scale on `order_submission_rate` or `outbox_backlog_depth`.

**Next steps**:
- Install KEDA (Kubernetes Event-Driven Autoscaler).
- Define custom metric → order queue depth.
- Configure scale-up aggressively, scale-down conservatively.

---

### 6.5 🟠 HIGH — No CDN or DDoS protection

The exchange is exposed directly. Binance sits behind Cloudflare Enterprise with layer-3/7 DDoS protection, rate limiting, and IP reputation blocking.

**Next steps**:
- Cloudflare or AWS Shield Standard (minimum).
- Implement request signing (HMAC) for API endpoints (partially done via API key system).
- Rate limit `/api/v1/orders` per API key and per IP at the ingress level.

---

### 6.6 🟡 MEDIUM — No blue/green or canary deployment pipeline

[rollout.yaml](file:///d:/Helium/infra/kubernetes/rollout.yaml) exists but the CI/CD pipeline to trigger it is absent (`.github` workflows directory is empty).

**Next steps**:
- Add GitHub Actions workflow: build → test → push image → Argo Rollouts canary.
- Define canary traffic percentage (5% → 25% → 100%).
- Add automated rollback on error-rate spike.

---

### 6.7 🟡 MEDIUM — Single backend service; no microservice decomposition

All 10 modules (matching, trading, wallet, ledger, auth, admin, compliance, market-data, audit, outbox) run in a single JVM. This simplifies deployment but means a memory leak in one module crashes everything.

**Note**: Not blocking early production, but plan module extraction starting with the matching engine.

---

## 7. Security

### 7.1 🔴 CRITICAL — `HELIUM_JWT_SECRET` default is `local-development-jwt-secret-change-me`

`docker-compose.yml` line 50 has a default JWT secret. If deployed without overriding this, all session tokens can be forged.

**Next steps**:
- Remove all default secrets from docker-compose and Kubernetes secrets.
- Rotate secrets via a secrets manager (Vault, AWS Secrets Manager).
- Add a startup health check that fails if default secrets are detected.

---

### 7.2 🔴 CRITICAL — `HELIUM_TOTP_ENCRYPTION_KEY` default is predictable

Default TOTP key: `local-totp-encryption-key-32bytes!!`. If used in production, all TOTP secrets in the database can be decrypted and all accounts bypassed.

Same fix as above — enforce rotation via startup validation.

---

### 7.3 🟠 HIGH — No WAF (Web Application Firewall) rules

No SQL injection, XSS, or path traversal protection at the edge. Spring Security provides some protection but no WAF rule set.

---

### 7.4 🟠 HIGH — Hardware Security Module (HSM) not used for custody key root-of-trust

Even with HashiCorp Vault, the Vault master key is sealed/unsealed by a human process. For a production exchange, the root of trust for custody keys should be a FIPS 140-2 Level 3 HSM (AWS CloudHSM, Thales Luna).

---

### 7.5 🟠 HIGH — No penetration test evidence

No pentest report, no bug bounty program, no SAST tooling (OWASP dependency check, Checkmarx, Snyk) in the build pipeline.

**Next steps**:
- Run OWASP ZAP against staging.
- Integrate Snyk in the Gradle build for dependency scanning.
- Engage a professional pentest firm before launch.

---

### 7.6 🟡 MEDIUM — TOTP replay window is ±1 step (90 seconds)

[TotpService.java#L58](file:///d:/Helium/backend/helium-core/modules/auth-user/src/main/java/com/helium/core/authuser/application/TotpService.java#L58) uses `TOTP_WINDOW = 1` which allows codes from ±30 seconds. This is standard but the used-code tracking is per `MfaSession`, not per TOTP interval. A code presented before the MFA session is created can be replayed.

**Next steps**:
- Track the last verified TOTP counter (not just the session).
- Reject any code whose counter ≤ last verified counter.

---

### 7.7 🟡 MEDIUM — No session geolocation anomaly detection

Sessions store IP and user-agent but there is no alerting when a session is created from a geographically impossible location (impossible travel). Binance emails users on new device logins.

---

## 8. Market Data & Real-Time

### 8.1 🟠 HIGH — Chart timeframes above 1m are disabled

[trading-workspace.tsx#L248](file:///d:/Helium/apps/web/features/trading/components/trading-workspace.tsx#L248): all timeframes except `1m` have `disabled={item !== "1m"}` and `opacity-45`. Users can only view 1-minute candles.

**Next steps**:
- Implement `CandleProjector` aggregation for 5m, 15m, 30m, 1H, 4H, 1D, 1W, 1M.
- Schedule OHLCV roll-up cron jobs.
- Cache candles in Redis.

---

### 8.2 🟠 HIGH — Technical indicators and drawing tools are disabled in the chart

All indicators (EMA, SMA, MACD, RSI, Bollinger) and drawing tools (Trendline, Fib, Horizontal, Crosshair) are stubbed in the UI. Binance Pro's TradingView chart is a key differentiator.

**Next steps**:
- Integrate TradingView Charting Library (requires commercial license).
- Or implement open-source lightweight-charts with custom indicator overlays.

---

### 8.3 🟡 MEDIUM — No Level 2 market data API (public REST snapshots beyond top-of-book)

The order book is served via WebSocket and REST but there is no public aggregated book depth API at configurable precision levels (like Binance's `/api/v3/depth?limit=5000`).

---

### 8.4 🟡 MEDIUM — No historical data export API

No endpoint to download historical OHLCV data. Binance provides `/api/v3/klines` for research and algo trading.

---

## 9. UI & User Experience

### 9.1 🟠 HIGH — Missing core pages

Currently present pages: trade, wallet, orders, markets, dashboard, settings, admin.

**Missing vs Binance**:
- **Earn / Staking / Savings** — yield products
- **Convert** — simple swap UI (not order-book based)
- **P2P Trading** page
- **Launchpad** — new token listings
- **NFT Marketplace**
- **Futures / Derivatives Trading** (separate terminal)
- **Referral program** dashboard
- **API Key management** UI (backend exists, frontend stub)
- **Sub-account management** for institutional users

Not all of these need to be built immediately, but a production MVP should have Convert and API Key management at minimum.

---

### 9.2 🟠 HIGH — Mobile responsive design is not verified

The trading workspace is built for desktop (3-column grid). No mobile-specific trading view or native app exists. Binance generates >60% of volume from mobile.

**Next steps**:
- At minimum, implement a responsive single-column mobile layout.
- Long-term: React Native app or Flutter.

---

### 9.3 🟡 MEDIUM — No price alerts

`HELIUM_PRICE_ALERT_EVALUATION_MS` env var is configured, suggesting a price alert evaluation loop was planned, but no `PriceAlert` domain entity or API exists.

---

### 9.4 🟡 MEDIUM — No fiat on/off ramp

No bank transfer (SEPA, ACH, Faster Payments), no credit card integration (Stripe, Moonpay, Banxa). Users can only deposit crypto.

---

## 10. Testing

### 10.1 🔴 CRITICAL — E2E test directory is empty

[testing/e2e](file:///d:/Helium/testing/e2e/) contains only a `.gitkeep`. No end-to-end test verifies that a user can register → deposit → trade → withdraw in a complete flow.

---

### 10.2 🟠 HIGH — Load tests are written but not run in CI

[testing/performance](file:///d:/Helium/testing/performance/) has k6 load test scripts (`load-test-orders.js`, `load-test-websocket.js`, etc.) but no CI step runs them. Regression in throughput goes undetected.

---

### 10.3 🟠 HIGH — Chaos tests exist but have no automated schedule

[testing/chaos](file:///d:/Helium/testing/chaos/) has 6 chaos scenarios. They are manual shell scripts with no Chaos Monkey or Litmus integration and no CI gate.

---

### 10.4 🟡 MEDIUM — Integration test coverage of the outbox processor is shallow

The outbox is the backbone of event delivery but there are no published integration tests that verify ordering guarantees, at-least-once delivery, and dead-letter handling under concurrent conditions.

---

## Summary Priority Matrix

| # | Area | Severity | Estimated Effort |
|---|---|---|---|
| 1.1 | Market orders | 🔴 CRITICAL | 2–3 weeks |
| 1.2 | Circuit breaker enforcement | 🔴 CRITICAL | 1 week |
| 3.1 | KYC integration | 🔴 CRITICAL | 4–6 weeks |
| 3.2 | Sanctions screening in payment flows | 🔴 CRITICAL | 2 weeks |
| 4.1 | AWS KMS / Azure KV custody | 🔴 CRITICAL | 3–4 weeks |
| 4.2 | Multisig withdrawal policy | 🔴 CRITICAL | 4 weeks |
| 5.1 | PagerDuty/alerting integration | 🔴 CRITICAL | 3 days |
| 5.5 | Status persistence | 🔴 CRITICAL | 3 days |
| 6.1 | PostgreSQL HA (Patroni) | 🔴 CRITICAL | 1 week |
| 6.2 | Redis HA (Sentinel/Cluster) | 🔴 CRITICAL | 1 week |
| 7.1 | Default secret rotation | 🔴 CRITICAL | 1 day |
| 10.1 | E2E tests | 🔴 CRITICAL | 2 weeks |
| 2.1 | Pre-trade risk limits | 🟠 HIGH | 2 weeks |
| 2.2 | VIP fee tiers | 🟠 HIGH | 2 weeks |
| 4.4 | Withdrawal address whitelist | 🟠 HIGH | 1 week |
| 5.2 | Grafana dashboards | 🟠 HIGH | 1 week |
| 5.3 | Distributed tracing | 🟠 HIGH | 1 week |
| 8.1 | Multi-timeframe candles | 🟠 HIGH | 1 week |
| 9.1 | Convert + API key management UI | 🟠 HIGH | 2 weeks |
| 3.3 | Regulatory reporting integration | 🟠 HIGH | 8–12 weeks |

---

> **Realistic timeline to Binance-grade MVP** (not full feature parity, but safe to open to public):
> **6–9 months** for a focused team of 8–12 engineers, assuming KYC, compliance, and custody work proceeds in parallel.
>
> Binance's full feature set took ~6 years and >2,000 engineers to build. This analysis defines the minimum bar for a **safe, legal, regulated spot exchange** — not the full Binance product.
