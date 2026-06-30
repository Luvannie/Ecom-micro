import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '5s',  target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:products-detail}': ['p(95)<30'],
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
// Sample product id (must exist in seeded DB)
const PRODUCT_ID = __ENV.PRODUCT_ID || '11111111-1111-1111-1111-111111111111';

export default function () {
    const url = `${BASE}/api/products/${PRODUCT_ID}`;
    const res = http.get(url, {
        tags: { endpoint: 'products-detail' },
    });
    check(res, {
        'status 200': (r) => r.status === 200,
    });
    sleep(1);
}
