import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';
import crypto from 'k6/crypto';

const replayBlocked = new Rate('replay_attacks_blocked');
const replayAttempts = new Counter('replay_attempts');

export const options = {
    scenarios: {
        replay_attacks: {
            executor: 'constant-vus',
            vus: 50,
            duration: '60s',
        },
    },
    thresholds: {
        'replay_attacks_blocked': ['rate>0.95'],
    },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'test-key';
const API_SECRET = __ENV.API_SECRET || 'test-secret';

function signRequest(method, path, timestamp, secret) {
    const canonical = `${method}\n${path}\n\n${timestamp}\n`;
    return crypto.hmac('sha256', secret, canonical, 'hex');
}

export default function () {
    const path = '/api/v1/orders/open';
    const timestamp = new Date().toISOString();
    const fixedNonce = `replay-nonce-${__VU}`;
    const signature = signRequest('GET', path, timestamp, API_SECRET);

    // First request should succeed
    const firstRes = http.get(`${BASE_URL}${path}`, {
        headers: {
            'X-API-Key': API_KEY,
            'X-API-Timestamp': timestamp,
            'X-API-Signature': signature,
            'X-API-Nonce': fixedNonce,
        },
        timeout: '5s',
    });

    // Replay the exact same request (same nonce) — should be rejected
    const replayRes = http.get(`${BASE_URL}${path}`, {
        headers: {
            'X-API-Key': API_KEY,
            'X-API-Timestamp': timestamp,
            'X-API-Signature': signature,
            'X-API-Nonce': fixedNonce,
        },
        timeout: '5s',
    });

    replayAttempts.add(1);
    const blocked = check(replayRes, {
        'replay rejected with 401': (r) => r.status === 401,
    });
    replayBlocked.add(blocked);

    // Test expired timestamp — should be rejected
    const oldTimestamp = new Date(Date.now() - 600000).toISOString(); // 10 minutes ago
    const oldSignature = signRequest('GET', path, oldTimestamp, API_SECRET);
    const expiredRes = http.get(`${BASE_URL}${path}`, {
        headers: {
            'X-API-Key': API_KEY,
            'X-API-Timestamp': oldTimestamp,
            'X-API-Signature': oldSignature,
            'X-API-Nonce': `expired-${Date.now()}`,
        },
        timeout: '5s',
    });

    check(expiredRes, {
        'expired timestamp rejected': (r) => r.status === 401,
    });

    sleep(1);
}

export function handleSummary(data) {
    return {
        stdout: `
=== Replay Attack Load Test Results ===
Total Replay Attempts: ${data.metrics.replay_attempts?.values?.count || 0}
Block Rate: ${((data.metrics.replay_attacks_blocked?.values?.rate || 0) * 100).toFixed(2)}%
========================================
`,
        'testing/performance/results/replay-attacks.json': JSON.stringify(data, null, 2),
    };
}
