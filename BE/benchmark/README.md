# Ecom Benchmark Suite

k6 load test scripts for backend performance optimization.

## Prerequisites

- Install k6: https://k6.io/docs/getting-started/installation/
- BE stack running (`docker compose up -d` + all services)

## Usage

```bash
# Set base URL (gateway)
export BASE=http://localhost:8080

# Run individual script
k6 run scripts/products-search.js

# Save baseline (date placeholder: actual YYYY-MM-DD)
mkdir -p baselines
k6 run --out json=baselines/2026-06-30-pre-products-search.json scripts/products-search.js

# Compare two runs
python3 scripts/compare.py \
  baselines/2026-06-30-pre-products-search.json \
  baselines/2026-06-30-post-products-search.json \
  > reports/2026-06-30-products-search.md
```

## Scripts

| File | Endpoint | Target p95 | Auth required |
|------|----------|------------|---------------|
| `products-search.js` | `GET /api/products?keyword=pizza&page=0` | < 100ms | No |
| `products-detail.js` | `GET /api/products/{id}` | < 30ms | No |
| `cart-add.js` | `POST /api/cart/items` | < 80ms | Yes (Bearer) |
| `cart-view.js` | `GET /api/cart` | < 30ms | Yes (Bearer) |

## Thresholds per script

Each script enforces `http_req_duration{p95} < target` and `http_req_failed rate < 0.01`.
