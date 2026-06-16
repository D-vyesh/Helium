import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const wsErrors = new Rate('ws_errors');
const wsLatency = new Trend('ws_message_latency_ms');
const wsMessages = new Counter('ws_messages_received');

export const options = {
    scenarios: {
        websocket_fanout: {
            executor: 'ramping-vus',
            startVUs: 50,
            stages: [
                { duration: '15s', target: 1000 },
                { duration: '60s', target: 5000 },
                { duration: '120s', target: 5000 },
                { duration: '15s', target: 0 },
            ],
        },
    },
    thresholds: {
        'ws_errors': ['rate<0.1'],
        'ws_message_latency_ms': ['p(95)<1000'],
    },
};

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080';
const MARKETS = ['BTC-USDT', 'ETH-USDT', 'SOL-USDT', 'ADA-USDT', 'DOT-USDT',
                 'LINK-USDT', 'UNI-USDT', 'AVAX-USDT', 'MATIC-USDT', 'ATOM-USDT'];
const TOKEN = __ENV.WS_TOKEN || 'test-session-token';

export default function () {
    const market = MARKETS[Math.floor(Math.random() * MARKETS.length)];
    const streams = ['trades', 'orderbook', 'ticker'];
    const stream = streams[Math.floor(Math.random() * streams.length)];
    const url = `${WS_URL}/ws/markets/${market}/${stream}?token=${TOKEN}`;

    const startTime = Date.now();

    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', () => {
            const connectTime = Date.now() - startTime;
            wsLatency.add(connectTime);
        });

        socket.on('message', (msg) => {
            wsMessages.add(1);
            try {
                const data = JSON.parse(msg);
                check(data, {
                    'has type field': (d) => d.type !== undefined,
                    'has topic field': (d) => d.topic !== undefined,
                });
            } catch (e) {
                wsErrors.add(true);
            }
        });

        socket.on('error', (e) => {
            wsErrors.add(true);
        });

        // Stay connected for 10-30 seconds
        const duration = 10000 + Math.random() * 20000;
        sleep(duration / 1000);

        socket.close();
    });

    const success = check(res, {
        'WebSocket connected': (r) => r && r.status === 101,
    });

    if (!success) {
        wsErrors.add(true);
    }

    sleep(1);
}

export function handleSummary(data) {
    return {
        stdout: `
=== WebSocket Fanout Load Test Results ===
Total Messages: ${data.metrics.ws_messages_received?.values?.count || 0}
Connection Error Rate: ${((data.metrics.ws_errors?.values?.rate || 0) * 100).toFixed(2)}%
P95 Connect Latency: ${data.metrics.ws_message_latency_ms?.values?.['p(95)']?.toFixed(2) || 'N/A'}ms
==========================================
`,
        'testing/performance/results/websocket.json': JSON.stringify(data, null, 2),
    };
}
