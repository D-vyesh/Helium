import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const failoverErrors = new Rate('failover_errors');
const failoverLatency = new Trend('failover_latency_ms');

export const options = {
    scenarios: {
        continuous_traffic: {
            executor: 'constant-vus',
            vus: 50,
            duration: '120s',
        },
    },
    thresholds: {
        'failover_errors': ['rate<0.05'],
        'failover_latency_ms': ['p(95)<5000'],
    },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';

export default function () {
    const res = http.get(`${BASE_URL}/actuator/health`, {
        timeout: '10s',
    });

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    failoverErrors.add(!success);
    failoverLatency.add(res.timings.duration);

    // Also hit API endpoints
    const apiRes = http.get(`${BASE_URL}/api/v1/markets`, {
        timeout: '10s',
    });
    check(apiRes, {
        'API responsive': (r) => r.status === 200 || r.status === 401,
    });

    sleep(0.5);
}

export function handleSummary(data) {
    return {
        stdout: `
=== Failover Load Test Results ===
Total Requests: ${data.metrics.http_reqs?.values?.count || 0}
Error Rate: ${((data.metrics.failover_errors?.values?.rate || 0) * 100).toFixed(2)}%
P95 Latency: ${data.metrics.failover_latency_ms?.values?.['p(95)']?.toFixed(2) || 'N/A'}ms
Recovery Time: measured by error window duration
==================================
`,
        'testing/performance/results/failover.json': JSON.stringify(data, null, 2),
    };
}
