// Cold-cache benchmark for products-detail. Each iteration picks a random
// product id so every request is a cache miss (no warm-up from previous VUs).
// Used to measure DB-only latency before Redis caches it.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '').split(',').filter(Boolean);

export const options = {
    scenarios: {
        cold: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.05'],
    },
};

export default function () {
    if (!TOKEN || PRODUCT_IDS.length === 0) return;
    const id = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
    const res = http.get(`${BASE}/api/products/${id}`, {
        headers: { 'Authorization': `Bearer ${TOKEN}` },
        tags: { endpoint: 'products-detail-cold' },
    });
    check(res, {
        'status 200': (r) => r.status === 200,
    });
}
