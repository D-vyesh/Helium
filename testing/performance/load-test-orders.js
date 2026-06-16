import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import crypto from 'k6/crypto';

const errorRate = new Rate('order_errors');
const orderLatency = new Trend('order_latency_ms');

export const options = {
    scenarios: {
        concurrent_orders: {
            executor: 'ramping-vus',
            startVUs: 100,
            stages: [
                { duration: '30s', target: 2000 },
                { duration: '60s', target: 10000 }, // 10,000 concurrent users
                { duration: '120s', target: 10000 }, // Sustained load (approx 5000 req/s with 0.1s sleep)
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        'order_errors': ['rate<0.05'],
        'order_latency_ms': ['p(95)<2000', 'p(99)<5000'],
    },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'test-key';
const API_SECRET = __ENV.API_SECRET || 'test-secret';
const MARKETS = ['BTC-USDT', 'ETH-USDT', 'SOL-USDT'];

function signRequest(method, path, timestamp, secret) {
    const canonical = `${method}\n${path}\n\n${timestamp}\n`;
    const signature = crypto.hmac('sha256', secret, canonical, 'hex');
    return signature;
}

export default function () {
    const timestamp = new Date().toISOString();
    const market = MARKETS[Math.floor(Math.random() * MARKETS.length)];
    const side = Math.random() > 0.5 ? 'BUY' : 'SELL';
    const price = (20000 + Math.random() * 10000).toFixed(2);
    const quantity = (0.001 + Math.random() * 0.1).toFixed(8);
    const clientOrderId = `load-test-${Date.now()}-${__VU}-${__ITER}`;
    const path = '/api/v1/orders';
    const signature = signRequest('POST', path, timestamp, API_SECRET);

    const payload = JSON.stringify({
        clientOrderId: clientOrderId,
        market: market,
        side: side,
        type: 'LIMIT',
        timeInForce: 'GTC',
        quantity: quantity,
        price: price,
    });

    const res = http.post(`${BASE_URL}${path}`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'X-API-Timestamp': timestamp,
            'X-API-Signature': signature,
            'X-API-Nonce': `${Date.now()}-${__VU}-${__ITER}`,
        },
        timeout: '10s',
    });

    const success = check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'has orderId': (r) => r.json('orderId') !== undefined || r.status === 401,
    });

    errorRate.add(!success);
    orderLatency.add(res.timings.duration);

    sleep(0.1);
}

export function handleSummary(data) {
    const p95 = data.metrics.order_latency_ms?.values?.['p(95)'] || 'N/A';
    const p99 = data.metrics.order_latency_ms?.values?.['p(99)'] || 'N/A';
    const errRate = data.metrics.order_errors?.values?.rate || 0;

    return {
        stdout: `
=== Order Placement Load Test Results ===
Total Requests: ${data.metrics.http_reqs?.values?.count || 0}
Error Rate: ${(errRate * 100).toFixed(2)}%
P95 Latency: ${typeof p95 === 'number' ? p95.toFixed(2) : p95}ms
P99 Latency: ${typeof p99 === 'number' ? p99.toFixed(2) : p99}ms
=========================================
`,
        'testing/performance/results/orders.json': JSON.stringify(data, null, 2),
    };
}
