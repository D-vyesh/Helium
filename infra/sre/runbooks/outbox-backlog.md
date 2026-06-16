# Runbook: Outbox Backlog / Dead Letters

## Symptoms
- Alert: `OutboxBacklogHigh`
- Dashboard shows `outbox_events_pending` > 1000.
- Dashboard shows increasing `outbox_events_dead_letter`.
- Users report delayed settlement or missing market data.

## Root Causes
1. **Downstream Service Failure**: A listener (e.g., Ledger, Matching, Wallet) is consistently throwing exceptions.
2. **Database Contention**: High lock contention on the `outbox_events` table preventing the polling thread from acquiring rows.
3. **Poison Pill Message**: A malformed event is continuously failing and exhausting retries.

## Mitigation Steps

### 1. Identify Failing Handlers
Query the database to see which event types are failing:
```sql
SELECT event_type, status, COUNT(*) 
FROM outbox_events 
GROUP BY event_type, status;
```

### 2. Check Application Logs
Search logs for `OutboxProcessor` and the specific handler (e.g., `SettlementOutboxEventHandler`).
```bash
kubectl logs -l app=helium-backend -n helium | grep "Outbox processing failed"
```

### 3. Handle Dead Letters
If there is a poison pill, it will eventually move to `DEAD_LETTER` status.
Review dead letters via the Admin API:
```bash
curl -X GET http://localhost:8080/api/v1/admin/outbox/dead-letters
```

To replay after deploying a fix:
```bash
curl -X POST http://localhost:8080/api/v1/admin/outbox/replay-all
```

### 4. Scale Polling Threads
If the backlog is purely due to volume, consider increasing the polling batch size or decreasing the interval via `application-production.yml`:
```yaml
helium.outbox.batch-size: 500
helium.outbox.poll-interval-ms: 500
```
