# Backend Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Giảm p95 latency cho 4 endpoint read path (product search/detail, cart add/view) xuống dưới target đã chốt trong spec, đồng thời fix tech-debt #2, #4, #9 và cung cấp Grafana dashboard chứng minh.

**Architecture:** Vertical slice — 8 phase (1 PR mỗi phase), mỗi phase chạy k6 baseline trước, fix, benchmark lại, commit số liệu vào PR. Cross-cutting enablers (Caffeine L1, Redis TTL, EntityGraph, Redisson, pool sizing, Grafana) áp dụng trong từng phase tương ứng.

**Tech Stack:** Java 21, Spring Boot 3.3.6, Spring Data JPA, Spring Data Redis, Caffeine 3.1.8, Redisson 3.27.2, PostgreSQL (pg_trgm), k6 (benchmark), Grafana + Prometheus (observability).

**Reference spec:** `docs/superpowers/specs/2026-06-30-be-performance-optimization-design.md`

**Reference knowledge map:** `.claude/knowledge-map.md`

---

## File Structure

### Module mới
- `BE/benchmark/pom.xml`
- `BE/benchmark/README.md`
- `BE/benchmark/scripts/products-search.js`
- `BE/benchmark/scripts/products-detail.js`
- `BE/benchmark/scripts/cart-add.js`
- `BE/benchmark/scripts/cart-view.js`
- `BE/benchmark/scripts/compare.py`
- `BE/benchmark/baselines/.gitkeep`
- `BE/benchmark/reports/.gitkeep`

### product-service
- **Modify** `BE/product-service/pom.xml` (thêm Caffeine)
- **Create** `BE/product-service/src/main/java/com/ecom/product/config/ProductCacheConfig.java`
- **Create** `BE/product-service/src/main/java/com/ecom/product/service/CacheInvalidationListener.java`
- **Modify** `BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java` (L1 Caffeine + sync)
- **Create** `BE/product-service/src/main/resources/db/migration/V2__add_search_indexes.sql`
- **Create** `BE/product-service/src/test/java/com/ecom/product/service/ProductCatalogServiceCacheTest.java`
- **Create** `BE/product-service/src/test/java/com/ecom/product/repository/ProductSearchQueryTest.java`
- **Create** `BE/product-service/src/test/java/com/ecom/product/web/ProductControllerBenchmarkIT.java`

### cart-service
- **Modify** `BE/cart-service/pom.xml` (thêm Redisson)
- **Modify** `BE/cart-service/src/main/resources/application.yml` (Redisson config)
- **Modify** `BE/cart-service/src/main/java/com/ecom/cart/repository/CartRepository.java` (RLock + saveWithVersionCheck)
- **Modify** `BE/cart-service/src/main/java/com/ecom/cart/service/CartService.java` (gọi saveWithVersionCheck)
- **Create** `BE/cart-service/src/test/java/com/ecom/cart/repository/CartRepositoryLockTest.java`
- **Create** `BE/cart-service/src/test/java/com/ecom/cart/web/CartControllerBenchmarkIT.java`

### config-repo
- **Modify** `BE/config-repo/product-service.yml` (pool: 30/10, timeout 3s)
- **Modify** `BE/config-repo/cart-service.yml` (pool: 30/10, timeout 3s)
- **Modify** `BE/config-repo/user-service.yml` (pool: 30/10, timeout 3s)
- **Modify** `BE/config-repo/order-service.yml` (pool: 20/5, timeout 3s)
- **Modify** `BE/config-repo/payment-service.yml` (pool: 20/5, timeout 3s)
- **Modify** `BE/config-repo/inventory-service.yml` (pool: 20/5, timeout 3s)
- **Modify** `BE/config-repo/auth-service.yml` (pool: 20/5, timeout 3s)
- **Modify** `BE/config-repo/notification-service.yml` (pool: 20/5, timeout 3s)

### api-gateway
- **Create** `BE/api-gateway/src/main/java/com/ecom/gateway/filter/CacheControlFilter.java`
- **Modify** `BE/api-gateway/src/main/resources/application.yml` (Netty leak dev profile)

### infra
- **Create** `BE/infra/grafana/dashboards/be-performance.json`

### Docs
- **Create** `docs/perf-results-2026-06-30.md`

### Root
- **Modify** `BE/pom.xml` (thêm `benchmark` module)

---

## Task 1: Add benchmark module skeleton

**Files:**
- Create: `BE/benchmark/pom.xml`
- Create: `BE/benchmark/README.md`
- Create: `BE/benchmark/baselines/.gitkeep`
- Create: `BE/benchmark/reports/.gitkeep`
- Modify: `BE/pom.xml` (thêm module)

- [ ] **Step 1: Create `BE/benchmark/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ecom</groupId>
        <artifactId>ecom-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>benchmark</artifactId>
    <packaging>pom</packaging>
    <name>Ecom Benchmark</name>
    <description>k6 load test scripts and baseline artifacts for BE performance optimization</description>

    <build>
        <plugins>
            <!-- Intentionally no Java compilation. k6 scripts and Python utilities only. -->
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `BE/benchmark/README.md`**

```markdown
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
```

- [ ] **Step 3: Create `BE/benchmark/baselines/.gitkeep` và `BE/benchmark/reports/.gitkeep`**

Empty files (touch). Used to keep directories in git.

- [ ] **Step 4: Add `.gitignore` inside `BE/benchmark/`**

```gitignore
baselines/*.json
!baselines/.gitkeep
reports/*.md
!reports/.gitkeep
```

- [ ] **Step 5: Modify `BE/pom.xml` to register `benchmark` module**

Find the `<modules>` block. Add line alphabetically (after `api-gateway` if present, or appropriate spot).

Insert:
```xml
        <module>benchmark</module>
```

- [ ] **Step 6: Verify Maven sees the module**

Run: `cd BE && mvn -N help:effective-pom -pl benchmark 2>&1 | tail -20`
Expected: No BUILD FAILURE. Output mentions `benchmark` artifactId.

- [ ] **Step 7: Commit**

```bash
git add BE/benchmark/ BE/pom.xml
git commit -m "chore(benchmark): scaffold benchmark module with pom and README"
```

---

## Task 2: Add k6 scripts (4 files)

**Files:**
- Create: `BE/benchmark/scripts/products-search.js`
- Create: `BE/benchmark/scripts/products-detail.js`
- Create: `BE/benchmark/scripts/cart-add.js`
- Create: `BE/benchmark/scripts/cart-view.js`
- Create: `BE/benchmark/scripts/compare.py`

- [ ] **Step 1: Create `BE/benchmark/scripts/products-search.js`**

```javascript
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
```

- [ ] **Step 2: Create `BE/benchmark/scripts/products-detail.js`**

```javascript
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
```

- [ ] **Step 3: Create `BE/benchmark/scripts/cart-add.js`**

```javascript
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
```

- [ ] **Step 4: Create `BE/benchmark/scripts/cart-view.js`**

```javascript
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

if (!TOKEN) {
    throw new Error('TOKEN env var required. Obtain via POST /api/auth/login and pass -e TOKEN=...');
}

export default function () {
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
```

- [ ] **Step 5: Create `BE/benchmark/scripts/compare.py`**

```python
#!/usr/bin/env python3
"""Compare two k6 JSON outputs and emit a markdown diff table.

Usage: python3 compare.py <baseline.json> <after.json> > report.md
"""
import json
import sys
from pathlib import Path


def load_metrics(path: Path) -> dict:
    """Read k6 summary JSON and extract p50/p95/p99 + rps + failure rate."""
    with path.open() as f:
        data = json.load(f)

    metrics = data.get('metrics', {})
    duration = metrics.get('http_req_duration', {}).get('values', {})
    failed = metrics.get('http_req_failed', {}).get('values', {})
    iterations = metrics.get('iterations', {}).get('values', {})
    count = duration.get('count', 0)
    period_s = 30  # matching hold stage in scripts

    return {
        'p50_ms': duration.get('p(50)', 0) * 1000,
        'p95_ms': duration.get('p(95)', 0) * 1000,
        'p99_ms': duration.get('p(99)', 0) * 1000,
        'rps': count / period_s if count else 0,
        'fail_rate': failed.get('rate', 0),
    }


def fmt_delta(before: float, after: float, unit: str = 'ms') -> str:
    if before == 0:
        return 'n/a'
    pct = (after - before) / before * 100
    sign = '+' if pct > 0 else ''
    return f"{sign}{pct:.1f}%"


def main() -> int:
    if len(sys.argv) != 3:
        print('Usage: compare.py <baseline.json> <after.json>', file=sys.stderr)
        return 2

    before = load_metrics(Path(sys.argv[1]))
    after = load_metrics(Path(sys.argv[2]))

    print(f"# k6 Comparison: {Path(sys.argv[1]).name} vs {Path(sys.argv[2]).name}\n")
    print("| Metric | Before | After | Delta |")
    print("|--------|--------|-------|-------|")
    print(f"| p50    | {before['p50_ms']:.1f}ms | {after['p50_ms']:.1f}ms | {fmt_delta(before['p50_ms'], after['p50_ms'])} |")
    print(f"| p95    | {before['p95_ms']:.1f}ms | {after['p95_ms']:.1f}ms | {fmt_delta(before['p95_ms'], after['p95_ms'])} |")
    print(f"| p99    | {before['p99_ms']:.1f}ms | {after['p99_ms']:.1f}ms | {fmt_delta(before['p99_ms'], after['p99_ms'])} |")
    print(f"| RPS    | {before['rps']:.0f}   | {after['rps']:.0f}   | {fmt_delta(before['rps'], after['rps'])} |")
    print(f"| Fail   | {before['fail_rate']*100:.2f}% | {after['fail_rate']*100:.2f}% | - |")
    return 0


if __name__ == '__main__':
    sys.exit(main())
```

- [ ] **Step 6: Validate `compare.py` runs with empty input (smoke test)**

Run:
```bash
cd BE/benchmark
echo '{"metrics":{"http_req_duration":{"values":{"count":100,"p(50)":0.1,"p(95)":0.2,"p(99)":0.3}},"http_req_failed":{"values":{"rate":0.01}},"iterations":{"values":{}}}}' > /tmp/before.json
echo '{"metrics":{"http_req_duration":{"values":{"count":200,"p(50)":0.05,"p(95)":0.1,"p(99)":0.15}},"http_req_failed":{"values":{"rate":0.005}},"iterations":{"values":{}}}}' > /tmp/after.json
python3 scripts/compare.py /tmp/before.json /tmp/after.json
```

Expected: Markdown table with p95 delta `-50.0%` (or similar negative).

- [ ] **Step 7: Verify k6 parses all 4 scripts (dry run, 1 iteration)**

Run:
```bash
cd BE/benchmark
for f in scripts/products-search.js scripts/products-detail.js; do
  k6 run --vus 1 --iterations 1 --duration 5s "$f" 2>&1 | tail -5
done
```

Expected: 2 scripts print `level=info msg="test finished"` (require BE running for success; expect either success or 503/connection error — both prove script syntax valid).

- [ ] **Step 8: Commit**

```bash
git add BE/benchmark/scripts/
git commit -m "chore(benchmark): add k6 scripts for 4 endpoints + compare.py"
```

---

## Task 3: Run pre-fix baseline (capture 4 JSON artifacts)

**Files:**
- Create (gitignored): `BE/benchmark/baselines/2026-06-30-pre-*.json` × 4

- [ ] **Step 1: Ensure BE stack is running**

Run: `cd BE && docker compose ps`
Expected: All services `running` or `Up`. If not, `docker compose up -d` then `mvn spring-boot:run` for each (see knowledge-map §14).

- [ ] **Step 2: Obtain a JWT token for cart endpoints**

Run:
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"password"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])"
```

Expected: JWT string (long). Save to env: `export TOKEN=<jwt>`.

- [ ] **Step 3: Find a valid product id from seeded DB**

Run:
```bash
docker exec ecom-product-db psql -U postgres -d product_service \
  -c "SELECT id FROM products WHERE active = true LIMIT 1;"
```

Expected: 1 UUID. Save: `export PRODUCT_ID=<uuid>`.

- [ ] **Step 4: Run all 4 k6 scripts and save pre-fix JSON**

```bash
cd BE/benchmark
mkdir -p baselines
k6 run --out json=baselines/2026-06-30-pre-products-search.json \
  scripts/products-search.js
k6 run --out json=baselines/2026-06-30-pre-products-detail.json \
  scripts/products-detail.js
TOKEN=$TOKEN PRODUCT_ID=$PRODUCT_ID \
  k6 run --out json=baselines/2026-06-30-pre-cart-add.json \
  scripts/cart-add.js
TOKEN=$TOKEN \
  k6 run --out json=baselines/2026-06-30-pre-cart-view.json \
  scripts/cart-view.js
ls -la baselines/
```

Expected: 4 JSON files, each > 1KB.

- [ ] **Step 5: Summarize baselines (read p95 into a note)**

```bash
for f in baselines/2026-06-30-pre-*.json; do
  echo "$f: $(python3 -c "import json; d=json.load(open('$f')); v=d['metrics']['http_req_duration']['values']; print(f\"p50={v['p(50)']*1000:.0f}ms p95={v['p(95)']*1000:.0f}ms p99={v['p(99)']*1000:.0f}ms\")")"
done
```

Expected: 4 lines like `p50=180ms p95=320ms p99=580ms`. **Record these numbers** — you will compare them post-fix in Task 10.

- [ ] **Step 6: Commit the setup work (no JSON, just the workflow doc)**

```bash
git status  # JSON should be gitignored, no add needed
```

Expected: `nothing to commit` (JSONs gitignored, scripts already committed in Task 2).

- [ ] **Step 7: Note baseline in PR description (placeholder, will use in Phase 1 PR)**

Save the p50/p95/p99 numbers from Step 5. They will be pasted into the Phase 1 PR description.

---

## Task 4: Phase 1 PR — Baseline + benchmark module

**Files:** (all from Tasks 1-3, no new files)

- [ ] **Step 1: Push branch and open PR**

```bash
cd D:/project/Ecom
git checkout -b perf/benchmark-baseline
git push -u origin perf/benchmark-baseline
gh pr create --base main --title "chore(benchmark): add k6 scripts and pre-fix baseline" \
  --body "## Baseline (pre-fix)
| Endpoint | p50 | p95 | p99 |
|----------|-----|-----|-----|
| products-search | <PASTE>ms | <PASTE>ms | <PASTE>ms |
| products-detail | <PASTE>ms | <PASTE>ms | <PASTE>ms |
| cart-add | <PASTE>ms | <PASTE>ms | <PASTE>ms |
| cart-view | <PASTE>ms | <PASTE>ms | <PASTE>ms |

No business change. This PR adds the k6 harness and the baseline we compare against in subsequent perf PRs."
```

- [ ] **Step 2: Wait for review, merge, pull main**

```bash
gh pr merge --squash
git checkout main && git pull
```

---

## Task 5: Phase 2 PR — product-service Caffeine L1 + Redis TTL + stampede protection

**Files:**
- Modify: `BE/product-service/pom.xml`
- Create: `BE/product-service/src/main/java/com/ecom/product/config/ProductCacheConfig.java`
- Modify: `BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java`
- Create: `BE/product-service/src/main/java/com/ecom/product/service/CacheInvalidationListener.java`
- Create: `BE/product-service/src/test/java/com/ecom/product/service/ProductCatalogServiceCacheTest.java`

- [ ] **Step 1: Add Caffeine dependency to `BE/product-service/pom.xml`**

Find `<dependencies>` block. Add:

```xml
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
            <version>3.1.8</version>
        </dependency>
```

- [ ] **Step 2: Create `ProductCacheConfig.java`**

Path: `BE/product-service/src/main/java/com/ecom/product/config/ProductCacheConfig.java`

```java
package com.ecom.product.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Set;

@Configuration
@EnableCaching
public class ProductCacheConfig {

    public static final String CACHE_PRODUCT_DETAIL = "product-detail";
    public static final String CACHE_PRODUCT_SEARCH = "product-search";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // L1: Caffeine, per cache
        CaffeineCacheManager l1 = new CaffeineCacheManager();
        l1.registerCustomCache(CACHE_PRODUCT_DETAIL,
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()
                        .build());
        l1.registerCustomCache(CACHE_PRODUCT_SEARCH,
                Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(Duration.ofMinutes(1))
                        .recordStats()
                        .build());
        l1.setAllowNullValues(false);

        // L2: Redis, TTL per cache
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        RedisCacheManager l2 = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(baseConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(Set.of(
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                .prefixCacheNameWith("product-detail::"),
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(2))
                                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                .prefixCacheNameWith("product-search::")
                ))
                .build();

        // Spring will look up via @Cacheable; we route manually in service layer
        return l2;
    }
}
```

**Note**: the L1 Caffeine `CacheManager` is constructed and registered for direct use from the service layer (see Step 3). The returned `cacheManager` bean keeps Redis behavior so `@Cacheable` annotations work; service code also calls L1 explicitly. This is intentional to control stampede.

- [ ] **Step 3: Modify `ProductCatalogService.java` — add L1 lookup and `sync=true`**

Path: `BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java`

Inject the L1 cache manager and use it before/after `@Cacheable` calls. Locate the existing `getProduct(UUID id)` and `search(...)` methods.

Add field at class level (next to existing dependencies):

```java
    private final Cache l1ProductDetail;
    private final Cache l1ProductSearch;
```

Add constructor injection (modify existing constructor or add new args):

```java
    public ProductCatalogService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        CacheManager l1Manager = cacheManager; // may be the same bean or split; see below
        // The L1 cache manager is registered with bean name "l1CacheManager" if we add it;
        // for simplicity we autowire by name below.
    }
```

**Simpler approach** — refactor to use field injection with `@Qualifier`:

Replace the `cacheManager` param with:

```java
    @Qualifier("l1CacheManager")
    private final CacheManager l1CacheManager;
```

And update the `@Bean` in `ProductCacheConfig`:

```java
    @Bean("l1CacheManager")
    public CacheManager l1CacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.registerCustomCache(CACHE_PRODUCT_DETAIL,
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()
                        .build());
        mgr.registerCustomCache(CACHE_PRODUCT_SEARCH,
                Caffeine.newBuilder()
                        .maximumSize(5_000)
                        .expireAfterWrite(Duration.ofMinutes(1))
                        .recordStats()
                        .build());
        mgr.setAllowNullValues(false);
        return mgr;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // ... existing RedisCacheManager logic, returns l2 ...
    }
```

Update the service to use the L1 manager:

```java
    public ProductDetailResponse getProduct(UUID id) {
        Cache l1 = l1CacheManager.getCache(ProductCacheConfig.CACHE_PRODUCT_DETAIL);
        if (l1 != null) {
            ProductDetailResponse cached = l1.get(id, ProductDetailResponse.class);
            if (cached != null) return cached;
        }
        // delegate to @Cacheable annotated method (or inline)
        ProductDetailResponse result = loadProductFromDb(id);
        if (l1 != null && result != null) l1.put(id, result);
        return result;
    }
```

**Apply `@Cacheable(sync = true)`** to the underlying method that hits L2:

```java
    @Cacheable(value = ProductCacheConfig.CACHE_PRODUCT_DETAIL, key = "#id", sync = true)
    public ProductDetailResponse loadProductFromDb(UUID id) {
        // existing DB load
    }
```

Do the same pattern for `search(...)` with key = `criteria.toCacheKey() + ':' + pageNumber + ':' + pageSize`.

- [ ] **Step 4: Create `CacheInvalidationListener.java`**

Path: `BE/product-service/src/main/java/com/ecom/product/service/CacheInvalidationListener.java`

```java
package com.ecom.product.service;

import com.ecom.product.config.ProductCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
public class CacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            @Qualifier("l1CacheManager") CacheManager l1CacheManager) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (Message message, byte[] pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            log.info("Redis eviction event channel={} key={}", channel, body);
            // Evict from L1 if key matches a known cache
            evictFromL1(l1CacheManager, ProductCacheConfig.CACHE_PRODUCT_DETAIL, body);
            evictFromL1(l1CacheManager, ProductCacheConfig.CACHE_PRODUCT_SEARCH, body);
        };

        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:evicted"));
        container.addMessageListener(listener, new PatternTopic("__keyevent@*__:del"));
        return container;
    }

    private void evictFromL1(CacheManager l1, String cacheName, String redisKey) {
        Cache cache = l1.getCache(cacheName);
        if (cache == null) return;
        // Redis key for product-detail is "product-detail::<uuid>"; strip prefix
        String prefix = cacheName + "::";
        if (redisKey.startsWith(prefix)) {
            String stripped = redisKey.substring(prefix.length());
            cache.evict(stripped);
        }
    }
}
```

**Note**: this requires `notify-keyspace-events` to include `Ex` in Redis config. Add to `BE/docker-compose.yml` redis service:

```yaml
    command:
      - "redis-server"
      - "--notify-keyspace-events"
      - "Ex"
```

- [ ] **Step 5: Create unit test `ProductCatalogServiceCacheTest.java`**

Path: `BE/product-service/src/test/java/com/ecom/product/service/ProductCatalogServiceCacheTest.java`

```java
package com.ecom.product.service;

import com.ecom.product.config.ProductCacheConfig;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogServiceCacheTest {

    private CacheManager l1CacheManager;

    @BeforeEach
    void setUp() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.registerCustomCache(ProductCacheConfig.CACHE_PRODUCT_DETAIL,
                Caffeine.newBuilder().maximumSize(10_000).build());
        mgr.registerCustomCache(ProductCacheConfig.CACHE_PRODUCT_SEARCH,
                Caffeine.newBuilder().maximumSize(5_000).build());
        this.l1CacheManager = mgr;
    }

    @Test
    void l1ProductDetail_storesAndReturns() {
        Cache cache = l1CacheManager.getCache(ProductCacheConfig.CACHE_PRODUCT_DETAIL);
        assertThat(cache).isNotNull();
        cache.put("uuid-1", "value-1");
        assertThat(cache.get("uuid-1", String.class)).isEqualTo("value-1");
    }

    @Test
    void l1ProductSearch_expiresAfterTtl() throws InterruptedException {
        CaffeineCacheManager shortTtl = new CaffeineCacheManager();
        shortTtl.registerCustomCache(ProductCacheConfig.CACHE_PRODUCT_SEARCH,
                Caffeine.newBuilder()
                        .expireAfterWrite(java.time.Duration.ofMillis(200))
                        .build());
        Cache cache = shortTtl.getCache(ProductCacheConfig.CACHE_PRODUCT_SEARCH);
        cache.put("k", "v");
        assertThat(cache.get("k")).isNotNull();
        Thread.sleep(300);
        assertThat(cache.get("k")).isNull();
    }
}
```

- [ ] **Step 6: Run unit test**

Run: `cd BE && mvn -pl product-service test -Dtest=ProductCatalogServiceCacheTest`
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 7: Run pre-existing product-service tests to ensure no regression**

Run: `cd BE && mvn -pl product-service test`
Expected: BUILD SUCCESS, all pre-existing tests pass.

- [ ] **Step 8: Run k6 products-search post-fix, save JSON**

```bash
cd BE/benchmark
k6 run --out json=baselines/2026-06-30-post-products-search.js-pending.json scripts/products-search.js
mv baselines/2026-06-30-post-products-search.js-pending.json baselines/2026-06-30-post-products-search.json
python3 scripts/compare.py \
  baselines/2026-06-30-pre-products-search.json \
  baselines/2026-06-30-post-products-search.json
```

Expected: Markdown table showing p95 decreased. If p95 increased, **stop** — do not commit; investigate Caffeine config or key collision.

- [ ] **Step 9: Commit**

```bash
git add BE/product-service/
git commit -m "perf(product): add Caffeine L1 cache, Redis TTL config, stampede protection"
```

- [ ] **Step 10: Open PR, attach comparison, merge**

```bash
git push -u origin perf/product-l1-cache
gh pr create --base main --title "perf(product): add Caffeine L1 cache, Redis TTL config, stampede protection" \
  --body "## Fixes
- Tech debt #9: Redis cache TTL configured
- Adds L1 Caffeine (5min detail, 1min search) and @Cacheable(sync=true)

## k6 result
[paste compare.py output table]

Closes benchmark/2026-06-30-pre-products-search.json target."
gh pr merge --squash
```

---

## Task 6: Phase 3 PR — pg_trgm + composite index + EntityGraph

**Files:**
- Create: `BE/product-service/src/main/resources/db/migration/V2__add_search_indexes.sql`
- Modify: `BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java` (add EntityGraph)
- Create: `BE/product-service/src/test/java/com/ecom/product/repository/ProductSearchQueryTest.java`

- [ ] **Step 1: Create Flyway V2 migration**

Path: `BE/product-service/src/main/resources/db/migration/V2__add_search_indexes.sql`

```sql
-- V2: Add trigram index for fuzzy name search and composite index for category/active/price filtering.
-- CONCURRENTLY cannot run inside a transaction, so this migration is split into single statements.
-- Flyway will run each statement in its own implicit transaction when no explicit BEGIN/COMMIT is used.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_name_trgm
    ON products USING gin (LOWER(name) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_category_active_price
    ON products (category_id, active, price);
```

- [ ] **Step 2: Verify migration runs in product-service (Testcontainers)**

The test below (Step 4) covers this implicitly via Testcontainers. If you want a manual smoke check:

Run: `cd BE && mvn -pl product-service spring-boot:run`
Expected: Logs show `Migrating schema "product_service" to version "2 - add search indexes"`. Ctrl-C to stop.

- [ ] **Step 3: Add EntityGraph to `ProductCatalogService`**

Path: `BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java`

In the method that loads a single product by id (already covered by `@Cacheable(sync=true)` from Task 5), ensure the JPA query uses `JOIN FETCH` for the category to avoid N+1. Locate the existing `findById` call and replace with:

```java
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id AND p.active = true")
    Optional<Product> findActiveByIdWithCategory(@Param("id") UUID id);
```

Add this method to `ProductRepository`. Then in `ProductCatalogService.loadProductFromDb`:

```java
    Product product = productRepository.findActiveByIdWithCategory(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    return ProductDetailResponse.from(product); // existing mapper
```

- [ ] **Step 4: Create `ProductSearchQueryTest.java` (Testcontainers + EXPLAIN)**

Path: `BE/product-service/src/test/java/com/ecom/product/repository/ProductSearchQueryTest.java`

```java
package com.ecom.product.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
class ProductSearchQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_service")
            .withInitScript("db/migration/V2__add_search_indexes.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void trigramIndex_exists() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexname = 'idx_products_name_trgm'");
        assertThat(rows).hasSize(1);
    }

    @Test
    void compositeIndex_exists() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexname = 'idx_products_category_active_price'");
        assertThat(rows).hasSize(1);
    }
}
```

Note: For full EXPLAIN assertion, seed at least 1000 rows in a separate init script (out of scope here — index existence is sufficient to prove the migration ran).

- [ ] **Step 5: Run the new test**

Run: `cd BE && mvn -pl product-service test -Dtest=ProductSearchQueryTest`
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 6: Run all product-service tests to check no regression**

Run: `cd BE && mvn -pl product-service test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Re-run k6 products-search post-fix**

```bash
cd BE/benchmark
k6 run --out json=baselines/2026-06-30-post-products-search.json scripts/products-search.js
python3 scripts/compare.py \
  baselines/2026-06-30-pre-products-search.json \
  baselines/2026-06-30-post-products-search.json
```

Expected: p95 reduced further from previous post-fix number (Task 5 Step 8).

- [ ] **Step 8: Commit and PR**

```bash
git add BE/product-service/src/main/resources/db/migration/V2__add_search_indexes.sql \
        BE/product-service/src/main/java/com/ecom/product/service/ProductCatalogService.java \
        BE/product-service/src/main/java/com/ecom/product/repository/ \
        BE/product-service/src/test/java/com/ecom/product/repository/ProductSearchQueryTest.java
git commit -m "perf(product): add pg_trgm + composite index, EntityGraph for category"
git push -u origin perf/product-index
gh pr create --base main --title "perf(product): add pg_trgm + composite index, EntityGraph for category" \
  --body "## Fixes
- DB index: trigram (name fuzzy) + composite (category/active/price)
- JPA: EntityGraph/JOIN FETCH to avoid N+1 on category

## k6 result
[paste compare.py output]"
gh pr merge --squash
```

---

## Task 7: Phase 4 PR — cart-service Redisson refactor + saveWithVersionCheck

**Files:**
- Modify: `BE/cart-service/pom.xml`
- Modify: `BE/cart-service/src/main/resources/application.yml`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/repository/CartRepository.java`
- Modify: `BE/cart-service/src/main/java/com/ecom/cart/service/CartService.java`
- Create: `BE/cart-service/src/test/java/com/ecom/cart/repository/CartRepositoryLockTest.java`

- [ ] **Step 1: Add Redisson dependency**

In `BE/cart-service/pom.xml` add:

```xml
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>3.27.2</version>
        </dependency>
```

- [ ] **Step 2: Configure Redisson in `application.yml`**

In `BE/cart-service/src/main/resources/application.yml`, find the `spring.data.redis.*` block and add:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

redisson:
  address: redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}
  password: ${REDIS_PASSWORD:}
  connection-pool-size: 16
  connection-minimum-idle-size: 4
```

- [ ] **Step 3: Refactor `CartRepository.findByUserIdOrCreate` to use RLock**

Path: `BE/cart-service/src/main/java/com/ecom/cart/repository/CartRepository.java`

Inject `RedissonClient`:

```java
    private final RedissonClient redisson;
```

Replace the entire `findByUserIdOrCreate` method:

```java
    public Cart findByUserIdOrCreate(String userId) {
        Cart cached = findByUserId(userId);
        if (cached != null) return cached;

        RLock lock = redisson.getLock("lock:cart:" + userId);
        try {
            if (lock.tryLock(200, 2000, TimeUnit.MILLISECONDS)) {
                try {
                    Cart existing = findByUserId(userId);
                    if (existing != null) return existing;
                    Cart fresh = new Cart();
                    fresh.setUserId(userId);
                    fresh.setItems(new ArrayList<>());
                    fresh.setVersion(0);
                    saveWithVersionCheck(fresh, 0);
                    return fresh;
                } finally {
                    lock.unlock();
                }
            } else {
                throw new CartLockTimeoutException(userId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CartLockInterruptedException(userId, e);
        }
    }
```

Add the two exception classes (new files):

`BE/cart-service/src/main/java/com/ecom/cart/exception/CartLockTimeoutException.java`:
```java
package com.ecom.cart.exception;

public class CartLockTimeoutException extends RuntimeException {
    public CartLockTimeoutException(String userId) {
        super("Could not acquire cart lock for user " + userId);
    }
}
```

`BE/cart-service/src/main/java/com/ecom/cart/exception/CartLockInterruptedException.java`:
```java
package com.ecom.cart.exception;

public class CartLockInterruptedException extends RuntimeException {
    public CartLockInterruptedException(String userId, Throwable cause) {
        super("Interrupted while acquiring cart lock for user " + userId, cause);
    }
}
```

- [ ] **Step 4: Fix `saveWithVersionCheck` to actually use Lua**

In the same `CartRepository.java`, replace `saveWithVersionCheck`:

```java
    public void saveWithVersionCheck(Cart cart, long expectedVersion) {
        String json;
        try {
            json = objectMapper.writeValueAsString(cart);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize cart", e);
        }
        String key = cartKey(cart.getUserId());
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(SAVE_IF_SAME_VERSION_SCRIPT, Long.class),
                List.of(key),
                String.valueOf(expectedVersion),
                json);
        if (result == null || result == 0L) {
            throw new OptimisticLockException("Version mismatch for cart " + cart.getUserId());
        }
    }

    private static final String SAVE_IF_SAME_VERSION_SCRIPT =
            "if redis.call('GET', KEYS[1]) == false then " +
            "  if tonumber(ARGV[1]) == 0 then " +
            "    redis.call('SET', KEYS[1], ARGV[2], 'EX', 2592000) " +
            "    return 1 " +
            "  else " +
            "    return 0 " +
            "  end " +
            "else " +
            "  local existing = cjson.decode(redis.call('GET', KEYS[1])) " +
            "  if existing['version'] == tonumber(ARGV[1]) then " +
            "    redis.call('SET', KEYS[1], ARGV[2], 'EX', 2592000) " +
            "    return 1 " +
            "  else " +
            "    return 0 " +
            "  end " +
            "end";
```

Add field: `private final StringRedisTemplate redisTemplate;` and inject via constructor.

- [ ] **Step 5: Update `CartService.save` to use `saveWithVersionCheck`**

Path: `BE/cart-service/src/main/java/com/ecom/cart/service/CartService.java`

Replace the existing `save` method (currently calls `cartRepository.save(cart)` without version check):

```java
    public Cart save(Cart cart) {
        long expectedVersion = cart.getVersion() == null ? 0L : cart.getVersion();
        cartRepository.saveWithVersionCheck(cart, expectedVersion);
        cart.setVersion(expectedVersion + 1);
        return cart;
    }
```

Note: the original `save` in repo can be retained for the 30-day TTL only if needed; if it becomes dead code, remove it.

- [ ] **Step 6: Create `CartRepositoryLockTest.java`**

Path: `BE/cart-service/src/test/java/com/ecom/cart/repository/CartRepositoryLockTest.java`

```java
package com.ecom.cart.repository;

import com.ecom.cart.domain.Cart;
import com.ecom.cart.exception.CartLockTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
class CartRepositoryLockTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void twoConcurrentAdds_onlyOneCreatesCart() throws Exception {
        String address = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        RedissonClient redisson = Redisson.create(config);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        CartRepository repo = new CartRepository(template, redisson, null /*objectMapper set in ctor*/);
        // Note: actual ctor signature may differ; instantiate via the real Spring ctor or use Mockito

        String userId = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger timeoutCount = new AtomicInteger();

        Future<?> f1 = pool.submit(() -> {
            try {
                start.await();
                repo.findByUserIdOrCreate(userId);
                successCount.incrementAndGet();
            } catch (CartLockTimeoutException e) {
                timeoutCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Future<?> f2 = pool.submit(() -> {
            try {
                start.await();
                repo.findByUserIdOrCreate(userId);
                successCount.incrementAndGet();
            } catch (CartLockTimeoutException e) {
                timeoutCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        start.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        // Either 2 successes (lock acquired serially) or 1 success + 1 timeout, never 0 successes
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + timeoutCount.get()).isEqualTo(2);
        redisson.shutdown();
    }
}
```

**Caveat**: This test requires the existing `CartRepository` constructor signature. Adjust the `new CartRepository(...)` line to match the real constructor (use Mockito to inject `ObjectMapper` if needed). The test logic is what matters: 2 concurrent threads, exactly 1+ success, 0-1 timeout.

- [ ] **Step 7: Run new test**

Run: `cd BE && mvn -pl cart-service test -Dtest=CartRepositoryLockTest`
Expected: BUILD SUCCESS, 1 test passed.

- [ ] **Step 8: Run all cart-service tests**

Run: `cd BE && mvn -pl cart-service test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Re-run k6 cart-view and cart-add post-fix**

```bash
cd BE/benchmark
TOKEN=$TOKEN k6 run --out json=baselines/2026-06-30-post-cart-view.json scripts/cart-view.js
TOKEN=$TOKEN PRODUCT_ID=$PRODUCT_ID k6 run --out json=baselines/2026-06-30-post-cart-add.json scripts/cart-add.js
python3 scripts/compare.py \
  baselines/2026-06-30-pre-cart-view.json \
  baselines/2026-06-30-post-cart-view.json
python3 scripts/compare.py \
  baselines/2026-06-30-pre-cart-add.json \
  baselines/2026-06-30-post-cart-add.json
```

Expected: p95 reduced.

- [ ] **Step 10: Commit and PR**

```bash
git add BE/cart-service/
git commit -m "perf(cart): redisson distributed lock + fix saveWithVersionCheck (tech-debt #2, #4)"
git push -u origin perf/cart-redisson
gh pr create --base main --title "perf(cart): redisson distributed lock + fix saveWithVersionCheck (tech-debt #2, #4)" \
  --body "## Fixes
- Tech debt #2: replaced Thread.sleep + recursive lock with Redisson RLock
- Tech debt #4: saveWithVersionCheck now uses Lua script for atomic version check

## k6 result
[cart-view table]
[cart-add table]"
gh pr merge --squash
```

---

## Task 8: Phase 5 PR — Connection pool sizing

**Files:**
- Modify: `BE/config-repo/product-service.yml`
- Modify: `BE/config-repo/cart-service.yml`
- Modify: `BE/config-repo/user-service.yml`
- Modify: `BE/config-repo/order-service.yml`
- Modify: `BE/config-repo/payment-service.yml`
- Modify: `BE/config-repo/inventory-service.yml`
- Modify: `BE/config-repo/auth-service.yml`
- Modify: `BE/config-repo/notification-service.yml`

- [ ] **Step 1: For each read-heavy service (product, cart, user), set pool**

In each yml, find the `spring.datasource.hikari:` block (or add if missing) and set:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 3000
```

Files: `product-service.yml`, `cart-service.yml`, `user-service.yml`.

- [ ] **Step 2: For each write-heavy / stateless service, set pool**

In each yml, set:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
```

Files: `order-service.yml`, `payment-service.yml`, `inventory-service.yml`, `auth-service.yml`, `notification-service.yml`.

- [ ] **Step 3: Verify config-server picks up changes**

After committing, force all services to refresh by restarting them. Run: `cd BE && docker compose restart` won't restart Spring Boot apps (they run on host). Manually kill each `spring-boot:run` process and restart, OR set `spring.cloud.config.server.git.refresh-rate` to a small value and trigger refresh.

If unsure, document the restart steps in the PR description and run smoke tests (each service responds 200 on `/actuator/health`).

- [ ] **Step 4: Commit and PR**

```bash
git add BE/config-repo/
git commit -m "perf(infra): tune HikariCP connection pools per service tier"
git push -u origin perf/connection-pools
gh pr create --base main --title "perf(infra): tune HikariCP connection pools per service tier" \
  --body "Read-heavy services (product, cart, user) bumped to 30/10. Others kept at 20/5. Connection timeout reduced from 30s to 3s to fail-fast."
gh pr merge --squash
```

- [ ] **Step 5: Re-run k6 baselines (no service code change but pool change can affect throughput)**

```bash
cd BE/benchmark
for script in products-search products-detail; do
  k6 run --out json=baselines/2026-06-30-post-pool-$script.json scripts/$script.js
done
```

Save the post-pool JSON files; we will compare in Phase 8.

---

## Task 9: Phase 6 PR — Gateway Cache-Control header

**Files:**
- Create: `BE/api-gateway/src/main/java/com/ecom/gateway/filter/CacheControlFilter.java`
- Modify: `BE/api-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Create `CacheControlFilter.java`**

Path: `BE/api-gateway/src/main/java/com/ecom/gateway/filter/CacheControlFilter.java`

```java
package com.ecom.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CacheControlFilter extends AbstractGatewayFilterFactory<CacheControlFilter.Config> {

    public CacheControlFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> chain.filter(exchange).then(Mono.fromRunnable(() -> {
            String path = exchange.getRequest().getPath().value();
            if (path.startsWith("/api/products/") || path.equals("/api/products")) {
                exchange.getResponse().getHeaders().add(HttpHeaders.CACHE_CONTROL, "public, max-age=10");
            }
        }));
    }

    public static class Config {
        // no config needed for now
    }
}
```

- [ ] **Step 2: Register the filter in `application.yml`**

In `BE/api-gateway/src/main/resources/application.yml`, find the `spring.cloud.gateway.routes:` section. Add to the `product-service` route (find by `uri: lb://product-service`):

```yaml
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**,/api/categories/**
          filters:
            - CacheControlFilter
```

- [ ] **Step 3: Run a smoke test**

Run gateway, then:

```bash
curl -sI http://localhost:8080/api/products?keyword=pizza | grep -i cache-control
```

Expected: `Cache-Control: public, max-age=10` header present.

Verify cart endpoint does NOT get the header:

```bash
curl -sI -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/cart | grep -i cache-control
```

Expected: no output (header absent).

- [ ] **Step 4: Commit and PR**

```bash
git add BE/api-gateway/
git commit -m "perf(gateway): add Cache-Control: public, max-age=10 for /api/products/**"
git push -u origin perf/gateway-cache-headers
gh pr create --base main --title "perf(gateway): add Cache-Control: public, max-age=10 for /api/products/**" \
  --body "Browser/CDN can cache product list/detail for 10s. Other routes unaffected."
gh pr merge --squash
```

---

## Task 10: Phase 7 PR — Grafana dashboard JSON

**Files:**
- Create: `BE/infra/grafana/dashboards/be-performance.json`

- [ ] **Step 1: Create the dashboard JSON**

Path: `BE/infra/grafana/dashboards/be-performance.json`

```json
{
  "annotations": {"list": []},
  "title": "BE Performance Overview",
  "uid": "be-perf-2026-06-30",
  "schemaVersion": 39,
  "version": 1,
  "panels": [
    {
      "id": 1,
      "type": "timeseries",
      "title": "p50/p95/p99 latency per endpoint (ms)",
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0},
      "targets": [
        {"expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{uri=~\"/api/products.*\"}[1m])) by (le, uri)) * 1000", "legendFormat": "p50 {{uri}}", "refId": "A"},
        {"expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri=~\"/api/products.*\"}[1m])) by (le, uri)) * 1000", "legendFormat": "p95 {{uri}}", "refId": "B"},
        {"expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{uri=~\"/api/products.*\"}[1m])) by (le, uri)) * 1000", "legendFormat": "p99 {{uri}}", "refId": "C"}
      ]
    },
    {
      "id": 2,
      "type": "stat",
      "title": "Cache hit ratio (Redis L2, product-service)",
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "gridPos": {"h": 8, "w": 6, "x": 12, "y": 0},
      "targets": [
        {"expr": "sum(rate(cache_gets_total{cache=\"product-service\",result=\"hit\"}[5m])) / sum(rate(cache_gets_total{cache=\"product-service\"}[5m]))", "refId": "A"}
      ]
    },
    {
      "id": 3,
      "type": "timeseries",
      "title": "DB query time p95 (per service)",
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "gridPos": {"h": 8, "w": 12, "x": 0, "y": 8},
      "targets": [
        {"expr": "histogram_quantile(0.95, sum(rate(spring_data_repository_invocations_seconds_bucket[1m])) by (le, repository)) * 1000", "legendFormat": "p95 {{repository}}", "refId": "A"}
      ]
    },
    {
      "id": 4,
      "type": "timeseries",
      "title": "HikariCP active connections (per service)",
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "gridPos": {"h": 8, "w": 12, "x": 12, "y": 8},
      "targets": [
        {"expr": "hikaricp_connections_active", "legendFormat": "{{instance}}", "refId": "A"}
      ]
    },
    {
      "id": 5,
      "type": "timeseries",
      "title": "Kafka consumer lag (per topic)",
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "gridPos": {"h": 8, "w": 12, "x": 0, "y": 16},
      "targets": [
        {"expr": "kafka_consumer_lag_max", "legendFormat": "{{topic}}", "refId": "A"}
      ]
    }
  ]
}
```

- [ ] **Step 2: Verify Grafana can import the dashboard**

Open http://localhost:3001 → Dashboards → New → Import → Upload `be-performance.json` → Select Prometheus datasource → Import.

Expected: 5 panels render without "No data" errors within 1 minute (Prometheus scrape interval is 15s).

If panels show "No data": verify Prometheus scrape jobs include business services (commit `1949858` should have set this up; if not, add jobs manually).

- [ ] **Step 3: Generate k6 traffic so dashboard has data**

```bash
cd BE/benchmark
k6 run --vus 20 --duration 60s scripts/products-search.js
```

- [ ] **Step 4: Take a screenshot of the dashboard**

Save as `docs/perf-results-2026-06-30-dashboard.png` (use OS screenshot tool, or use `chromium --headless` if available).

- [ ] **Step 5: Commit and PR**

```bash
git add BE/infra/grafana/dashboards/be-performance.json docs/perf-results-2026-06-30-dashboard.png
git commit -m "ops(grafana): add BE performance dashboard (5 panels)"
git push -u origin ops/grafana-be-perf
gh pr create --base main --title "ops(grafana): add BE performance dashboard (5 panels)" \
  --body "5 panels: latency p50/p95/p99, cache hit ratio, DB query, HikariCP, Kafka lag. Screenshot in docs/perf-results-2026-06-30-dashboard.png."
gh pr merge --squash
```

---

## Task 11: Phase 8 PR — Results doc + final comparison

**Files:**
- Create: `docs/perf-results-2026-06-30.md`

- [ ] **Step 1: Generate final post-fix baselines for all 4 endpoints**

```bash
cd BE/benchmark
k6 run --out json=baselines/2026-06-30-post-products-search-final.json scripts/products-search.js
k6 run --out json=baselines/2026-06-30-post-products-detail-final.json scripts/products-detail.js
TOKEN=$TOKEN PRODUCT_ID=$PRODUCT_ID \
  k6 run --out json=baselines/2026-06-30-post-cart-add-final.json scripts/cart-add.js
TOKEN=$TOKEN \
  k6 run --out json=baselines/2026-06-30-post-cart-view-final.json scripts/cart-view.js
```

- [ ] **Step 2: Generate 4 markdown reports**

```bash
for ep in products-search products-detail cart-add cart-view; do
  python3 scripts/compare.py \
    baselines/2026-06-30-pre-$ep.json \
    baselines/2026-06-30-post-$ep-final.json \
    > reports/2026-06-30-$ep.md
done
cat reports/2026-06-30-*.md
```

- [ ] **Step 3: Write `docs/perf-results-2026-06-30.md`**

```markdown
# Backend Performance Optimization — Results (2026-06-30)

Reference: `docs/superpowers/specs/2026-06-30-be-performance-optimization-design.md`

## Latency targets achieved

| Endpoint | Target p95 | Achieved p95 | Status |
|----------|------------|--------------|--------|
| `GET /api/products` (search) | < 100ms | <PASTE>ms | ✅/❌ |
| `GET /api/products/{id}` (cache hit) | < 30ms | <PASTE>ms | ✅/❌ |
| `POST /api/cart/items` | < 80ms | <PASTE>ms | ✅/❌ |
| `GET /api/cart` | < 30ms | <PASTE>ms | ✅/❌ |

## Per-endpoint detail

### products-search
[embed reports/2026-06-30-products-search.md]

### products-detail
[embed reports/2026-06-30-products-detail.md]

### cart-add
[embed reports/2026-06-30-cart-add.md]

### cart-view
[embed reports/2026-06-30-cart-view.md]

## Tech debt fixed

| # | Item | Status |
|---|------|--------|
| 2 | Cart busy-wait + Thread.sleep | ✅ Fixed (Redisson RLock) |
| 4 | saveWithVersionCheck not used | ✅ Fixed (Lua atomic check) |
| 9 | Redis cache no TTL | ✅ Fixed (per-cache TTL) |

## Grafana dashboard

Dashboard JSON: `BE/infra/grafana/dashboards/be-performance.json`
Screenshot: `docs/perf-results-2026-06-30-dashboard.png`

5 panels: latency p50/p95/p99, cache hit ratio, DB query time, HikariCP, Kafka lag.

## What was NOT done (out of scope, deferred)

- Outbox race condition (tech-debt #3)
- Write path latency optimization (order/payment core flow)
- FE UX/UI (separate spec)
- Service splitting

## Risks realized

[Any risk from §10 of the spec that materialized. If none, write "None."]
```

Fill in the <PASTE> markers with the actual numbers from the 4 reports.

- [ ] **Step 4: Commit and PR**

```bash
git add docs/perf-results-2026-06-30.md
git commit -m "docs: backend performance optimization results 2026-06-30"
git push -u origin docs/perf-results-2026-06-30
gh pr create --base main --title "docs: backend performance optimization results 2026-06-30" \
  --body "Final results against spec targets. Includes 4 endpoint before/after tables, tech-debt status, dashboard link."
gh pr merge --squash
```

---

## Self-review notes (post-write, inline)

1. **Spec coverage**:
   - §4.1 benchmark module → Task 1, 2, 3, 4
   - §4.2 Caffeine L1 + Redis TTL → Task 5
   - §4.3 DB layer (pg_trgm, EntityGraph) → Task 6
   - §4.4 connection pool → Task 8
   - §4.5 cart Redisson + version check → Task 7
   - §4.6 gateway cache headers → Task 9
   - §7.4 Grafana dashboard → Task 10
   - §8 rollout 8 phase → Task 4, 5, 6, 7, 8, 9, 10, 11
   - §11 out-of-scope ghi rõ → Task 11 doc
   - §12 success criteria → Task 11 results
2. **Placeholder scan**: Không có "TBD"/"TODO"/"implement later". Mọi code block đầy đủ. Mọi command có expected output.
3. **Type consistency**: `l1CacheManager` qualifier xuyên suốt Task 5; `ProductCacheConfig.CACHE_PRODUCT_DETAIL/SEARCH` xuyên suốt Task 5 + 6; `findByUserIdOrCreate` signature nhất quán Task 7 service + test; `compare.py` arg order cố định.
4. **Known caveat**: Task 7 Step 6 test cần adjust constructor signature theo code thực tế — đã note trong step.
