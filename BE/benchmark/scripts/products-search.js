import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '5s',  target: 0  },
    ],
    thresholds: {
        'http_req_duration{endpoint:products-search}': ['p(95)<100'],
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const KEYWORD = __ENV.KEYWORD || 'pizza';

export default function () {
    const url = `${BASE}/api/products?keyword=${KEYWORD}&page=0&size=20`;
    const res = http.get(url, {
        tags: { endpoint: 'products-search' },
    });
    check(res, {
        'status 200': (r) => r.status === 200,
        'has data field': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.data?.content);
            } catch {
                return false;
            }
        },
    });
    sleep(1);
}
