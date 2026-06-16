# Error Budget Policy

## Overview
An error budget represents the acceptable level of unreliability for the HELIUM Exchange. If our SLO is 99.9%, our error budget is 0.1% of requests within the measurement window (typically 28 days).

## Burn Rates & Alerting
We use multi-burn-rate alerting:
1.  **Fast Burn (Page)**: Error rate consumes 2% of the budget in 1 hour. This triggers a critical page to the on-call engineer.
2.  **Slow Burn (Ticket)**: Error rate consumes 5% of the budget in 6 hours. This creates a high-priority ticket for the team.

## Policy Enforcement
If the error budget is exhausted (< 0% remaining for the 28-day window):
1.  **Feature Freeze**: All non-critical feature deployments are halted.
2.  **Focus Shift**: Engineering efforts are redirected exclusively to reliability, performance improvements, and tech debt resolution.
3.  **Post-Mortem**: A comprehensive review of the incidents that drained the budget must be conducted and remediation items prioritized.

Normal feature deployment resumes only when the rolling 28-day budget recovers above 0%.
