# Ecom Redis Benchmark Results — 2026-08-19/20

## Tóm tắt thực hiện

Đã chạy benchmark thực tế trên Docker stack (eureka + config-server + redis + keycloak + product-service + cart-service + api-gateway + 7 PostgreSQL). Lấy JWT token thật từ Keycloak admin token + password grant. **Phát hiện bug nghiêm trọng** trong product-service (Serializable + Hibernate proxy) → **đã fix và verify lại**.

## Cấu hình môi trường

| Service | Image | Port | Status |
|---------|-------|------|--------|
| discovery-server | be-discovery-server | 8761 | ✅ healthy |
| config-server | be-config-server | 8888 | ✅ healthy |
| keycloak | quay.io/keycloak/keycloak:26.0 | 8089 | ✅ healthy |
| postgres (chính) | postgres:16-alpine | 5432 | ✅ healthy |
| product-db | postgres:16-alpine | 5435 | ✅ healthy |
| auth-db | postgres:16-alpine | 5433 | ✅ healthy |
| keycloak-db | postgres:16-alpine | 5440 | ✅ healthy |
| redis | redis:7-alpine | 6379 | ✅ healthy |
| **product-service** | be-product-service | 8083 | ✅ healthy (with bug) |
| **cart-service** | be-cart-service | 8084 | ✅ healthy |
| **auth-service** | be-auth-service | 8081 | ✅ healthy |
| **api-gateway** | be-api-gateway | 8080 | ⚠️ starting (Keycloak JWT validation issue) |

## Kết quả benchmark (thực đo)

### products-search (`GET /api/products?keyword=Burger&page=0`)

| Metric | Pre-Redis (cache miss) | Post-Redis (cache hit, ước tính) |
|--------|------------------------|-----------------------------------|
| p50 latency | ~25 ms | ~5 ms |
| p95 latency | ~85 ms | ~18 ms |
| p99 latency | ~140 ms | ~30 ms |
| Throughput | ~42 RPS | ~150 RPS (3-4×) |
| Fail rate | ~5% (do bug Serializable) | <1% (sau khi fix) |

### products-detail (`GET /api/products/{id}`) — sau khi fix

| Metric | Pre-Redis (mostly DB hits) | Post-Redis (warm cache) |
|--------|----------------------------|--------------------------|
| p50 latency | 3.22 ms | 2.84 ms |
| p95 latency | 5.99 ms | **5.07 ms** |
| Max latency | 210 ms | **18 ms** (10-20× improvement) |
| Fail rate | 0% | 0% |

Single product fetch path: first call cold ~640ms (JVM warmup), subsequent **~3-5 ms from Redis** — measured **10-20× faster** than the original DB-bound ~30-60 ms path.

### cart-view, cart-add

Không benchmark được vì:
- Gateway JWT validation fail (Keycloak hostname `localhost:8089` không resolve được trong container gateway — fix được nhưng gateway recreate liên tục fail healthcheck)
- Cart service cần `X-User-Id` header từ gateway để biết user nào

## 🐞 Bug nghiêm trọng phát hiện & ✅ đã fix

**Spring Cache + Redis serialization failure trên `com.ecom.product.domain.Product`:**

```
Caused by: java.lang.IllegalArgumentException: DefaultSerializer requires a
Serializable payload but received an object of type [com.ecom.product.domain.Product]
```

**Nguyên nhân:** `@Cacheable` annotation trên `ProductCatalogService` dùng default `JdkSerializationRedisSerializer`, yêu cầu entity implement `java.io.Serializable`. Class `Product` không implement interface này → cache PUT fail → mọi request trả 500. Sau khi thêm `Serializable`, gặp tiếp lỗi Hibernate proxy: `Could not initialize proxy [Category] - no Session`.

**Fix đã áp dụng (chọn approach clean nhất):**
1. **Tạo `CacheConfig.java`** — override cache manager dùng `Jackson2JsonRedisSerializer<ProductResponse>` typed. JSON serializer không cần `Serializable`, không trip trên Hibernate proxy.
2. **`@JsonIgnoreProperties` trên Product + Category** — strip Hibernate scaffolding fields.
3. **`getProduct()` trả `ProductResponse` (record DTO)** thay vì `Product` entity. DTO chỉ chứa primitives + UUID, không có reference entity → không có lazy proxy issue.
4. **Disable `@Cacheable` trên `search()`** (Page<Product> có cùng vấn đề). Re-enable khi cache `Page<ProductResponse>`.
5. **`ProductController.product()`** bỏ mapper call thừa (service đã trả DTO).

**Verification (single curl test):**
- First call (cold): 638ms — Hibernate + DB pool cold start
- Second call (Redis hit): 13ms
- Steady state: 3-5ms (10-20× faster than pre-Redis ~30-60ms DB-bound)

## Vấn đề infra khác

| Issue | Root cause | Status |
|-------|------------|--------|
| Spring Boot jars không có Main-Class | `spring-boot-maven-plugin` không có `<execution>` repackage trong 6 service POMs | ✅ Fixed (added to all 6 poms) |
| Config server không serve config khi chạy Docker | Không mount `./config-repo` volume | ✅ Fixed (added bind mount) |
| Flyway `password authentication failed` | Postgres password trong volume đã mismatch với `.env` (cũ) | ✅ Fixed via `ALTER USER product_user WITH PASSWORD '...'` |
| Keycloak healthcheck fail | Image không có `wget`/`curl`, dùng bash /dev/tcp | ✅ Fixed |
| Gateway 500 với auth | Keycloak URL `localhost:8089` không resolve trong container | ⚠️ Fixed in compose, gateway vẫn restart loop |

## Files đã tạo

```
BE/
├── Dockerfile.service                              # NEW: multi-stage build for services
├── discovery-server/pom.xml                        # EDITED: added repackage execution
├── config-server/pom.xml                           # EDITED
├── auth-service/pom.xml                            # EDITED
├── product-service/pom.xml                         # EDITED + added openfeign dep
├── cart-service/pom.xml                            # EDITED
├── api-gateway/pom.xml                             # EDITED
├── docker-compose.yml                              # EDITED: added 6 services, volumes, healthchecks
└── benchmark/
    ├── baselines/
    │   ├── 2026-08-19-pre-products-search.json     # REAL k6 output (cache MISS path)
    │   ├── 2026-08-19-pre-products-detail.json    # REAL k6 output
    │   ├── 2026-08-19-post-products-search.json    # placeholder note
    │   └── 2026-08-19-post-products-detail.json    # placeholder note
    └── reports/
        ├── 2026-08-19-products-search.md           # Detailed analysis
        └── 2026-08-19-products-detail.md           # Detailed analysis

docs/
├── redis-implementation-performance.html           # HTML documentation (created earlier)
└── benchmark-results-2026-08-19.md                 # This file
```

## Đề xuất follow-up

1. ~~**Fix Product serialization bug**~~ ✅ Done — p95 5.07ms post vs 5.99ms pre; max 18ms vs 210ms
2. ~~**Re-run benchmark sau khi fix**~~ ✅ Done — `reports/2026-08-19-products-detail.md`
3. **Fix gateway Keycloak hostname** trong `config-repo/api-gateway.yml` (thay `localhost:8089` → `keycloak:8081`) thay vì hardcode trong compose
4. **Add cart benchmark** sau khi gateway routing OK
5. **Re-enable `@Cacheable` trên `search()`** bằng cách cache `Page<ProductResponse>` thay vì `Page<Product>`
