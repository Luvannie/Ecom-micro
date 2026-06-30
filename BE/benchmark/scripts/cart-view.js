import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '5s',  target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:cart-view}': ['p(95)<30'],
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;

export default function () {
    if (!TOKEN) {
        // Surface as a k6 check failure with a clear label, not a module-load crash.
        check(__ENV, {
            'TOKEN env var is set (obtain via POST /api/auth/login and pass -e TOKEN=...)': (e) => !!e.TOKEN,
        });
        return;
    }
    const url = `${BASE}/api/cart`;
    const res = http.get(url, {
        headers: { 'Authorization': `Bearer ${TOKEN}` },
        tags: { endpoint: 'cart-view' },
    });
    check(res, {
        'status 200': (r) => r.status === 200,
    });
    sleep(1);
}
