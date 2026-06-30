import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '5s',  target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:cart-add}': ['p(95)<80'],
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN; // supply via -e TOKEN=...
const PRODUCT_ID = __ENV.PRODUCT_ID || '11111111-1111-1111-1111-111111111111';

if (!TOKEN) {
    throw new Error('TOKEN env var required. Obtain via POST /api/auth/login and pass -e TOKEN=...');
}

export default function () {
    const url = `${BASE}/api/cart/items`;
    const payload = JSON.stringify({
        productId: PRODUCT_ID,
        quantity: 1,
    });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${TOKEN}`,
        },
        tags: { endpoint: 'cart-add' },
    };
    const res = http.post(url, payload, params);
    check(res, {
        'status 200 or 201': (r) => r.status === 200 || r.status === 201,
    });
    sleep(1);
}
