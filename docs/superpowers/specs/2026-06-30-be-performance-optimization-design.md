# Backend Performance Optimization — Design Spec

> **Date**: 2026-06-30
> **Status**: Approved (awaiting user review)
> **Author**: brainstorming session
> **Related**: Spec #2 (Frontend UX/UI) — to follow; `.claude/knowledge-map.md` mục 13 (tech debt)

---

## 1. Context

Ecom là food-delivery microservices (12 services, Java 21 + Spring Boot 3.3.6, React FE). Yêu cầu brainstorm **tối ưu performance** đã được tách thành 2 sub-spec độc lập. Đây là **Spec #1: Backend Performance**; phạm vi đã chốt với user qua 4 câu hỏi clarifying:

- **Mục tiêu**: Giảm latency (p95/p99) — KHÔNG phải throughput, KHÔNG phải resource efficiency, KHÔNG phải tech-debt cleanup thuần túy
- **Phạm vi**: Gateway & auth + Cross-cutting (Kafka/Redis) + Read path (catalog/search/cart). KHÔNG bao gồm write path (order/payment core logic)
- **Ràng buộc**: Full freedom — code + schema + config đều OK
- **Success criteria**: Cả 3 — số đo cụ thể + xóa tech debt + Grafana chứng minh

Knowledge-map tại `.claude/knowledge-map.md` mục 13 liệt kê 17 tech-debt items. Trong đó 4 items đã được xử lý bởi commits gần đây (Resilience4j: `a5db7b7`, `4c94a1d`, `0340f03`, `f047a92`, `caa52b6`; GlobalExceptionHandler scan: `b085d8d`; Prometheus scrape: `1949858`). Spec này xử lý tiếp các items liên quan read path: **#2** (cart busy-wait), **#4** (saveWithVersionCheck), **#9** (Redis cache TTL). Items còn lại sẽ được ghi rõ là out-of-scope.

## 2. Approach

**Approach A: Vertical slice optimization** (đã chốt với user).

4 endpoint slice đi từ gateway → service → data, mỗi slice: benchmark baseline → fix → benchmark after. Kèm 6 cross-cutting enabler làm trước để dùng cho mọi slice.

## 3. Architecture & Boundaries

### 3.1 Endpoint slices

| # | Endpoint | Path qua | Service | Phương pháp chính |
|---|----------|----------|---------|---------------------|
| 1 | `GET /api/products?keyword=&categoryId=&page=` | Gateway route | product-service | L1 Caffeine + L2 Redis + JPA Specification + pg_trgm |
| 2 | `GET /api/products/{id}` | Gateway route | product-service | L1 Caffeine + L2 Redis (TTL 10min) + EntityGraph |
| 3 | `POST /api/cart/items` | Gateway auth → cart-service | cart-service | Redisson lock + Feign ProductClient (slice 2 cache hit) |
| 4 | `GET /api/cart` | Gateway auth → cart-service | cart-service | Redis GET (direct JSON parse, bỏ DTO mapping thừa) |

### 3.2 Cross-cutting enablers (làm trước, dùng cho mọi slice)

1. **Caffeine L1 cache** trên product-service (sub-ms in-process, giảm Redis round-trip)
2. **TTL config** cho `@Cacheable` (fix tech-debt #9)
3. **Cache stampede protection** (`@Cacheable(sync=true)`)
4. **Connection pool sizing** per service (hiện default 20/5)
5. **JPA `EntityGraph`/`JOIN FETCH`** chống N+1
6. **Grafana dashboard** scrape tất cả services (Prometheus jobs đã có từ commit `1949858`, cần dashboard JSON)

### 3.3 Module mới

- `BE/benchmark/` — k6 scripts + baseline artifacts. KHÔNG thêm service mới, KHÔNG thay đổi service hiện tại ngoài các điểm đã liệt kê.

## 4. Components & Tech choices

### 4.1 `BE/benchmark/`

- **Tool**: k6 (Grafana official, có sẵn binary, chạy local + GitHub Actions)
- **Config**: 50 VU × 30s, ramp-up 10s, ramp-down 5s
- **Thresholds**: `http_req_duration{p95} < target` (xem §8), `http_req_failed < 1%`
- **Output**: JSON summary lưu vào `BE/benchmark/baselines/<date>-<pre|post>-<endpoint>.json`
- **Files**:
  - `products-search.js`
  - `products-detail.js`
  - `cart-add.js`
  - `cart-view.js`
  - `compare.py` — parse 2 JSON, sinh bảng markdown so sánh p50/p95/p99
  - `README.md` — hướng dẫn chạy

### 4.2 Cache layer (product-service)

- **L1 Caffeine**: `com.github.ben-manes.caffeine:caffeine:3.1.8`
  - `product-detail` cache: max 10K entries, `expireAfterWrite=5min`
  - `product-search` cache: max 5K entries, `expireAfterWrite=1min`
- **L2 Redis** (giữ nguyên `@Cacheable`):
  - TTL: detail=10min, search=2min (qua `RedisCacheConfiguration` per-cache)
  - Serializer: `GenericJackson2JsonRedisSerializer` (kiểm tra config hiện tại trước khi đổi)
- **Stampede protection**: `@Cacheable(sync=true)` cho `search()`; với `getProduct(id)` dùng Caffeine `LoadingCache` (single-flight trong process) + `sync=true` cho L2
- **Cross-node invalidation**: subscribe `__keyevent@*__:evicted` qua `RedisMessageListenerContainer` để evict L1 khi L2 bị expire/evict ở node khác (R2 mitigation)

### 4.3 DB layer (product-service)

- **Index mới** trong Flyway V2:
  - `CREATE EXTENSION IF NOT EXISTS pg_trgm;` (ngoài transaction)
  - `CREATE INDEX CONCURRENTLY idx_products_name_trgm ON products USING gin (LOWER(name) gin_trgm_ops);`
  - `CREATE INDEX CONCURRENTLY idx_products_category_active_price ON products (category_id, active, price);`
- **EntityGraph**: `findById` và `search` return `Page<Product>` thêm `attributePaths={"category"}` để chống N+1
- **JPA batch**: `spring.jpa.properties.hibernate.jdbc.batch_size=50` cho admin bulk operation

### 4.4 Connection pool

| Service tier | Services | max-pool | min-idle | timeout |
|--------------|----------|----------|----------|---------|
| Read-heavy | product, cart, user | 30 | 10 | 3s |
| Write-heavy | order, payment, inventory | 20 | 5 | 3s |
| Stateless | auth, notification | 20 | 5 | 3s |

Config trong `BE/config-repo/<service>.yml`. Expose metric `hikaricp_connections_active` qua `actuator/prometheus` (mặc định Spring Boot đã có).

### 4.5 Cart (cart-service)

- **Thêm dependency**: `org.redisson:redisson-spring-boot-starter:3.27.2`
- **Refactor `CartRepository.findByUserIdOrCreate`**: thay `setIfAbsent + Thread.sleep(50) + recursive` bằng `RLock` (`lock:cart:{userId}`, wait 200ms, lease 2s)
- **Sửa `saveWithVersionCheck`**: gọi `SET_IF_SAME_SCRIPT` (Lua) thật sự check `version` thay vì Java-side check (fix tech-debt #4)
- **`getCart`**: bỏ mapping DTO thừa, return trực tiếp từ JSON parse

### 4.6 Gateway (api-gateway)

- **Response caching**: filter thêm header `Cache-Control: public, max-age=10` cho `GET /api/products/**` (không áp dụng cho cart/user/orders)
- **Verify Resilience4j**: route `login/register/refresh/products` (commit `0340f03`) đã có RequestRateLimiter; kiểm tra config `TimeLimiter` + `Bulkhead` propagate xuống downstream
- **Dev profile**: `spring.reactor.netty.io.netty.leakDetection.level=PARANOID`

## 5. Data flow (slice 1 mẫu — các slice khác tương tự)

```
[Client]
   │ GET /api/products?keyword=pizza&page=0
   ▼
[API Gateway :8080]   target: 2ms
   │ • RequestRateLimiter (NORMAL 100 req/s)
   │ • JwtAuthenticationFilter (skip public path)
   │ • GatewayCorrelationIdFilter add X-Correlation-Id
   │ • add header X-Cache: HIT|L1|L2|MISS  ← observable
   ▼
[Product Service :8083]   target p95 < 80ms
   │ 1. L1 Caffeine check (key=criteriaKey+page+size) — 0.5ms
   │ 2. Nếu MISS → L2 Redis GET product-search::{key} — 2-5ms
   │ 3. Nếu MISS → JPA Specification + EntityGraph — 30-60ms
   │      • SQL: WHERE active=true AND LOWER(name) LIKE '%pizza%'
   │      •             AND category_id = ? AND price BETWEEN ? AND ?
   │      • Uses idx_products_name_trgm, idx_products_category_active_price
   │      • Page<Product> hydrated với category JOIN
   │ 4. Set L1 (TTL 1min) + L2 (TTL 2min)
   │ 5. Return Page<ProductSummaryResponse>
   ▼
[Client]   total target p95 < 100ms
```

**Stampede protection**:
- Spring 6 `@Cacheable(sync=true)`: dùng internal lock, các request sau chờ request đầu populate
- Caffeine `LoadingCache.get(key, mappingFunction)`: trong process, single-flight

**Cache eviction** (admin update):
- `@CacheEvict(allEntries=true)` trên `createProduct`/`updateProduct`/`changeStatus` (đã có, KHÔNG đổi)
- L1 invalidation qua Redis pub/sub (R2 mitigation)

## 6. Error handling & resilience

| Tình huống | Hành vi |
|------------|---------|
| L2 Redis down | Skip cache, query DB, log warn, return 200 (degraded) |
| L1 Caffeine stale sau L2 expire ở node khác | Lắng nghe `__keyevent@*__:evicted` → evict L1 entry tương ứng |
| DB transient error | Resilience4j Retry 2 lần × 100ms backoff (đã có từ commit `caa52b6`) → fail-fast 503 nếu vẫn fail |
| Stampede lock Redis timeout (200ms) | Fallback query DB trực tiếp, KHÔNG throw |
| Redisson lock contention | Wait 200ms, nếu không acquire → retry 1 lần, fail-fast 503 |
| Benchmark p95 sau fix > baseline | Rollback PR, document lý do, không merge |

## 7. Testing & measurement

### 7.1 Benchmark

- Script k6 mẫu (mục 4.1)
- Workflow: chạy pre-fix baseline → save JSON → implement fix → chạy lại → save post-fix JSON → `compare.py` sinh báo cáo
- Báo cáo mẫu (`BE/benchmark/reports/2026-06-30-products-search.md`):
  ```
  | Metric | Pre-fix | Post-fix | Delta |
  |--------|---------|----------|-------|
  | p50    | 180ms   | 35ms     | -80%  |
  | p95    | 320ms   | 78ms     | -76%  |
  | p99    | 580ms   | 145ms    | -75%  |
  | RPS    | 480     | 1200     | +150% |
  ```

### 7.2 Unit test mới (target 80% cho code mới)

- `ProductCatalogServiceCacheTest` — Mockito verify L1 + L2 interaction
- `CartRepositoryLockTest` — 2 thread concurrent add, expect 1 thắng, 1 retry thành công
- `ProductSearchQueryTest` — Testcontainers Postgres, verify `EXPLAIN` dùng index mới

### 7.3 Integration test mới

- `ProductControllerBenchmarkIT` — `@SpringBootTest` + embedded Redis, gọi 1000 request, assert p95 < target
- `CartControllerBenchmarkIT` — tương tự

### 7.4 Grafana dashboard (`infra/grafana/dashboards/be-performance.json`)

| Panel | Query | Visualization |
|-------|-------|----------------|
| 1. p50/p95/p99 latency per endpoint | `http_server_requests_seconds{quantile=...}` group by uri | Timeseries, 4 series |
| 2. Cache hit ratio (L1, L2) | Custom counter `cache_hits_total{cache="l1\|l2"}` từ product-service | Stat / Timeseries |
| 3. DB query time p95 | `pg_stat_statements` (cần enable extension) | Timeseries |
| 4. Kafka consumer lag | `kafka_consumer_lag_max` (kafka-exporter) | Timeseries |
| 5. HikariCP active connections | `hikaricp_connections_active` per service | Timeseries |

Datasource: Prometheus (`http://prometheus:9090`, đã có trong docker-compose).

## 8. Rollout

| Phase | PR | Nội dung | Phụ thuộc | Risk |
|-------|----|----|----|------|
| P1 | `chore(benchmark): add k6 scripts + baseline` | Module `BE/benchmark/`, chạy baseline pre-fix cho 4 endpoint | none | none |
| P2 | `perf(product): add L1 cache + stampede protection` | Caffeine + Redis TTL + `@Cacheable(sync=true)` + L1 invalidation pub/sub | P1 | R2 (cross-node stale) |
| P3 | `perf(product): add trigram + composite index` | Flyway V2 + EntityGraph | P1 | R1 (concurrent index build) |
| P4 | `perf(cart): redisson lock + version check` | Redisson dep + refactor + fix #4 | P1 | R3 (Redis connection) |
| P5 | `perf(infra): tune connection pools + metrics` | yml changes per service | none | none |
| P6 | `perf(gateway): cache-control headers` | Gateway filter | P2 (để có cache hit để cache) | none |
| P7 | `ops(grafana): add BE performance dashboard` | JSON dashboard | P5 | none |
| P8 | `docs: BE perf results` | So sánh baseline/after, đính kèm số liệu trong `docs/perf-results-2026-06-30.md` | P2-P7 | none |

Mỗi PR chạy `k6 run` baseline so với HEAD, đính kèm output vào PR description.

## 9. Dependencies (pom.xml additions)

| Service | Dependency | Version |
|---------|-----------|---------|
| product-service | `com.github.ben-manes.caffeine:caffeine` | 3.1.8 |
| cart-service | `org.redisson:redisson-spring-boot-starter` | 3.27.2 |
| Postgres (Flyway V2) | extension `pg_trgm` | built-in |

## 10. Risks & mitigations

| ID | Risk | Mitigation |
|----|------|-----------|
| R1 | pg_trgm GIN index build lâu trên bảng lớn | `CREATE INDEX CONCURRENTLY` ngoài transaction; chạy offline nếu prod |
| R2 | Caffeine L1 stale sau khi Redis evict ở node khác | `RedisMessageListenerContainer` lắng nghe `__keyevent@*__:evicted`; fallback TTL 5min cũng giới hạn tối đa staleness |
| R3 | Redisson thêm 1 connection Redis riêng | Check connection budget; tận dụng shared connection pool |
| R4 | 4 endpoint p95 target khác nhau, có thể không đạt | Dừng, reassess, KHÔNG tiếp tục ép; rollback PR nếu regression |

## 11. Out of scope (ghi rõ để tránh creep)

- **Outbox race condition** (tech-debt #3) — write path
- **Create order / payment latency** — write path
- **FE UX/UI** — Spec #2
- **Migrations trên DB production lớn** (>1M rows) — V2 phải chạy offline
- **Kafka consumer optimization** — đã có wrapper ở commit `4c94a1d`, không cải thiện thêm
- **Service splitting / microservice refactor**
- **Tech-debt items #1, #3, #5, #6, #7, #8, #10, #11, #12, #13, #14, #15, #16, #17** — không liên quan read path latency. Note: #5, #6, #12 đã được fix bởi commits gần đây.

## 12. Success criteria (đo được)

### 12.1 Latency targets

| Endpoint | Baseline p95 (ước lượng) | Target p95 | Đo bằng |
|----------|--------------------------|------------|---------|
| `GET /api/products` (search) | 200-400ms | **< 100ms** | k6 `products-search.js` |
| `GET /api/products/{id}` (cache miss) | 80-150ms | **< 30ms** (cache hit) | k6 `products-detail.js` |
| `POST /api/cart/items` | 100-200ms | **< 80ms** | k6 `cart-add.js` |
| `GET /api/cart` | 30-60ms | **< 30ms** | k6 `cart-view.js` |

### 12.2 Tech debt

| Item | Trạng thái | Cách xác nhận |
|------|------------|---------------|
| #2 cart busy-wait | Fixed (Redisson) | `CartRepositoryLockTest` concurrent test pass |
| #4 saveWithVersionCheck | Fixed | `findByUserIdOrCreate` gọi Lua check version |
| #9 Redis cache TTL | Fixed | `RedisCacheConfiguration` có TTL per-cache |

### 12.3 Grafana

- 5 panel dashboard import OK từ `infra/grafana/dashboards/be-performance.json`
- Scrape mọi service đã có job (commit `1949858`)
- 1 screenshot dashboard khi chạy k6 baseline, đính kèm trong `docs/perf-results-2026-06-30.md`

## 13. Open questions (resolved trong session này)

| # | Question | Answer |
|---|----------|--------|
| Q1 | Mục tiêu chính? | Latency giảm |
| Q2 | Phạm vi hot-path? | Gateway & auth + Cross-cutting + Read path |
| Q3 | Ràng buộc thay đổi? | Full freedom |
| Q4 | Success criteria? | Cả 3 (số đo + tech debt + dashboard) |
| Q5 | Approach? | A: Vertical slice |
| Q6-Q11 | Section approval? | All approved |

## 14. Next step

Sau khi user review spec này và đồng ý → invoke `superpowers:writing-plans` để tạo implementation plan chi tiết cho 8 phase ở mục 8.

Sau khi plan xong → quay lại brainstorm **Spec #2: Frontend UX/UI** (sẽ dùng cùng process: scope → questions → approach → 6 sections → spec).
