// Cold-cache benchmark for products-detail. Sequential (1 VU) so every
// request is a cache miss, isolating DB-only latency. Used as the
// pre-Redis baseline.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '').split(',').filter(Boolean);

export const options = {
    scenarios: {
        cold: {
            executor: 'constant-vus',
            vus: 1,
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
    sleep(0.05); // small breathing room so we don't drown the JVM
}
