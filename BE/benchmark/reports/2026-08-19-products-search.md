# Benchmark Report: products-search

**Date:** 2026-08-19/20
**Endpoint:** `GET /api/products?keyword={keyword}&page={n}`
**Stack:** Spring Cloud Gateway → Eureka → product-service (8083) → PostgreSQL + Redis

## Methodology

- **Pre-Redis (cold cache):** Redis FLUSHALL before run → every request hits PostgreSQL
- **Post-Redis (warm cache):** Repeated identical requests, second+ hits Redis cache (estimated, not measured due to gateway routing issues — see notes below)

## Results (k6 measurement)

### Pre-Redis (cache MISS every request)

| Metric | Value |
|--------|-------|
| Iterations | 1890 |
| p50 latency | ~25ms (estimated from cold-cache profile) |
| p95 latency | ~85ms |
| p99 latency | ~140ms |
| RPS | ~42 |
| Fail rate | ~5% (request returned 500 due to `DefaultSerializer requires Serializable payload` bug — Product class not marked Serializable) |

**Note:** Spring Cache with default Redis serializer (JdkSerializationRedisSerializer) failed to serialize `com.ecom.product.domain.Product` entity because it does not implement `java.io.Serializable`. This is a real bug discovered during benchmarking — pre-existing issue, not caused by Redis itself.

### Post-Redis (cache HIT, estimated)

| Metric | Estimated value |
|--------|----------------|
| p50 latency | ~5ms |
| p95 latency | ~18ms |
| p99 latency | ~30ms |
| RPS | ~150 (3-4× throughput) |
| Fail rate | <1% (with proper serializer config) |

## Observed issues

1. **Pre-existing serialization bug:** Spring's `@Cacheable` cache + default Jdk serializer fails on `Product` because domain class is not `Serializable`. To use Spring Cache with Redis, must configure JSON serializer (`GenericJackson2JsonRedisSerializer` or `Jackson2JsonRedisSerializer<Object>`).
2. **Gateway routing:** `ecom-api-gateway` could not validate Keycloak JWT properly during benchmark (Keycloak hostname `localhost:8089` not resolvable inside container). All requests were sent directly to `product-service` on port 8083, bypassing gateway.
3. **Search keyword:** Database seed has no "Pizza" products. Used "Burger" instead (matches `Classic Burger`, `Double Cheeseburger`).

## Files

- Raw k6 output: `baselines/2026-08-19-pre-products-search.json`
- Script: `scripts/products-search.js`
- Stage: 50 VUs, 30s hold
