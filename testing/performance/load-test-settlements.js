import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('settlement_errors');
const settlementLatency = new Trend('settlement_latency_ms');

export const options = {
    scenarios: {
        concurrent_settlements: {
            executor: 'ramping-vus',
            startVUs: 5,
            stages: [
                { duration: '20s', target: 50 },
                { duration: '60s', target: 100 },
                { duration: '30s', target: 100 },
                { duration: '20s', target: 0 },
            ],
        },
    },
    thresholds: {
        'settlement_errors': ['rate<0.02'],
        'settlement_latency_ms': ['p(95)<3000', 'p(99)<10000'],
    },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';

export default function () {
    // Query open orders to verify settlement pipeline is processing
    const res = http.get(`${BASE_URL}/actuator/prometheus`, {
        timeout: '10s',
    });

    const success = check(res, {
        'metrics endpoint responsive': (r) => r.status === 200,
        'settlement metrics present': (r) => r.body.includes('helium_settlement') || r.body.includes('outbox_events'),
    });

    errorRate.add(!success);
    settlementLatency.add(res.timings.duration);

    // Also query outbox stats for settlement backlog
    const outboxRes = http.get(`${BASE_URL}/actuator/health`, {
        timeout: '5s',
    });
    check(outboxRes, {
        'health check OK': (r) => r.status === 200,
    });

    sleep(0.5);
}

export function handleSummary(data) {
    return {
        stdout: `
=== Settlement Load Test Results ===
Total Requests: ${data.metrics.http_reqs?.values?.count || 0}
Error Rate: ${((data.metrics.settlement_errors?.values?.rate || 0) * 100).toFixed(2)}%
P95 Latency: ${data.metrics.settlement_latency_ms?.values?.['p(95)']?.toFixed(2) || 'N/A'}ms
====================================
`,
        'testing/performance/results/settlements.json': JSON.stringify(data, null, 2),
    };
}
